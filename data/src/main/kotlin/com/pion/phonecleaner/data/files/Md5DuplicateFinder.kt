package com.pion.phonecleaner.data.files

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DuplicateGroup
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.DuplicateFinder
import com.pion.phonecleaner.domain.repository.FileDigest
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * The duplicate pipeline, in one place
 * (`docs/screens/14-file-tools-and-app-manager.md` §2.2, §2.4).
 *
 * It **composes** the three shared primitives and walks nothing of its own (§0.1). Five stages:
 *
 *  1. collect every row the current grant reaches — the three media collections;
 *  2. dedupe by path, so one file listed in two collections is one candidate;
 *  3. group by **exact byte size**, keeping groups larger than one. This is the cheap pre-filter the
 *     competitor does not have, and without it every same-size-different-content file is read end to
 *     end;
 *  4. digest the candidates, bounded by [MAX_DIGESTS];
 *  5. group by digest, newest member first.
 *
 * **A budget that expires publishes what completed and says so.** The competitor's
 * `withTimeoutOrNull(4000)` truncates its hashing loop and still reports success, so "no duplicates"
 * can mean "we stopped looking" — a silently wrong answer, which is worse than a slow one.
 *
 * DECLARED IN `filesDataModule`; `internal`, so no other module can bind it.
 */
internal class Md5DuplicateFinder(
    private val mediaStore: MediaStoreRepository,
    private val digest: FileDigest,
    private val dispatchers: DispatcherProvider,
    @Suppress("unused") private val scanner: StorageScanner,
) : DuplicateFinder {

    override fun find(): Flow<DuplicateScanProgress> = flow {
        val candidates = sizeCandidates()
        emit(DuplicateScanProgress.Hashing(hashed = 0, candidates = candidates.size))

        val byDigest = LinkedHashMap<String, MutableList<ScannedFile>>()
        var hashed = 0
        var truncated = false
        for (file in candidates) {
            if (hashed >= MAX_DIGESTS) {
                truncated = true
                break
            }
            val hex = (digest.digest(file) as? AppResult.Success)?.value
            hashed++
            if (hex != null) byDigest.getOrPut(hex) { mutableListOf() } += file
            if (hashed % PROGRESS_EVERY == 0) {
                emit(DuplicateScanProgress.Hashing(hashed, candidates.size))
            }
        }

        emit(
            DuplicateScanProgress.Finished(
                groups = byDigest.entries
                    .filter { it.value.size > 1 }
                    .map { (hex, files) -> group(hex, files) }
                    .sortedByDescending(DuplicateGroup::reclaimableBytes)
                    .toImmutableList(),
                truncated = truncated,
            ),
        )
    }.flowOn(dispatchers.io)

    /**
     * Stage 1–3. `StorageScanner` is a constructor argument and deliberately unused **for now**: in
     * the default branch a duplicate outside the three media collections is reachable only through a
     * SAF tree, and walking those trees for a content hash is the expensive half of a feature whose
     * §10 item 6 says its corpus changes with the storage branch. The dependency is declared so the
     * branch switch is a change to this one method rather than to the Koin graph.
     */
    private suspend fun sizeCandidates(): List<ScannedFile> {
        val rows = LinkedHashMap<String, ScannedFile>()
        for (result in listOf(mediaStore.images(), mediaStore.videos(), mediaStore.audio())) {
            (result as? AppResult.Success)?.value?.forEach { rows.putIfAbsent(it.path, it) }
        }
        return rows.values
            .filter { it.sizeBytes >= MIN_CANDIDATE_BYTES }
            .groupBy { it.sizeBytes }
            .values
            .filter { it.size > 1 }
            .flatten()
    }

    private fun group(hex: String, files: List<ScannedFile>): DuplicateGroup {
        val newest = files.maxByOrNull(ScannedFile::lastModifiedAtMillis) ?: files.first()
        return DuplicateGroup(
            md5 = hex,
            files = files.sortedByDescending(ScannedFile::lastModifiedAtMillis).toImmutableList(),
            newestId = newest.id,
        )
    }

    private companion object {
        /**
         * UNKNOWN — no source states a digest budget. It replaces a 4 s wall clock whose expiry was
         * reported as success; a count is bounded the same way and is reportable, which a wall clock
         * on a slow device is not.
         */
        const val MAX_DIGESTS = 2_000

        /** A zero-byte file matches every other zero-byte file and is not a duplicate worth listing. */
        const val MIN_CANDIDATE_BYTES = 1L

        const val PROGRESS_EVERY = 16
    }
}
