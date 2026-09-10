package com.pion.phonecleaner.data.files

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.domain.model.file.DuplicateGroup
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.DuplicateFinder
import com.pion.phonecleaner.domain.repository.FileDigest
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The duplicate pipeline, in one place
 * (`docs/screens/14-file-tools-and-app-manager.md` §2.2, §2.4).
 *
 * It **composes** the shared primitives and walks nothing of its own (§0.1). Stages 1–3 —
 * collect, dedupe, size pre-filter — are `DuplicateCandidates.kt`, and this file is stages 4 and 5:
 *
 *  4. digest, **twice**: a 64 KiB head over every candidate, then a full read only where a head
 *     collided and the file is longer than that window. A same-size-different-content pair costs one
 *     buffer instead of two end-to-end reads, which is what makes a whole-volume corpus affordable;
 *  5. group by digest, newest member first, biggest reclaim first.
 *
 * **A budget that expires publishes what completed and says so.** The competitor's
 * `withTimeoutOrNull(4000)` truncates its hashing loop and still reports success, so "no duplicates"
 * can mean "we stopped looking" — a silently wrong answer, which is worse than a slow one. The
 * budget lives HERE and not only on the ViewModel, because a timeout at the *collector* throws the
 * partial answer away while one at the *producer* publishes it.
 *
 * DECLARED IN `filesDataModule`; `internal`, so no other module can bind it.
 */
internal class Md5DuplicateFinder(
    private val mediaStore: MediaStoreRepository,
    private val scanner: StorageScanner,
    private val roots: StorageRootProvider,
    private val permissions: PermissionRepository,
    private val digest: FileDigest,
    private val dispatchers: DispatcherProvider,
) : DuplicateFinder {

    override fun find(): Flow<DuplicateScanProgress> = flow {
        val deadline = TimeSource.Monotonic.markNow() + ScanBudget
        val rows = collectRows(
            mediaStore = mediaStore,
            scanner = scanner,
            roots = roots,
            permissions = permissions,
            maxDepth = MaxDepth,
            budget = CollectBudget,
            progressEvery = CollectProgressEvery,
        )
        val candidates = sizeCandidates(rows, MinCandidateBytes)
        emit(DuplicateScanProgress.Hashing(hashed = 0, candidates = candidates.size))

        val heads = digestPass(
            digest = digest,
            candidates = candidates,
            maxBytes = FileDigest.HEAD_BYTES,
            parallelism = DigestParallelism,
            deadline = deadline,
            onProgress = hashingProgress(candidates.size),
        )
        val (proven, unproven) = heads.partitionByHead()

        val full = digestPass(
            digest = digest,
            candidates = unproven,
            maxBytes = FileDigest.FULL_FILE,
            parallelism = DigestParallelism,
            deadline = deadline,
            hashedBefore = heads.hashed,
            onProgress = hashingProgress(candidates.size + unproven.size),
        )
        full.digests.groupBy { (_, hex) -> hex }
            .forEach { (hex, pairs) -> if (pairs.size > 1) proven[hex] = pairs.map { it.first } }

        emit(
            DuplicateScanProgress.Finished(
                groups = proven.map { (hex, files) -> group(hex, files) }
                    .sortedByDescending(DuplicateGroup::reclaimableBytes)
                    .toImmutableList(),
                truncated = heads.truncated || full.truncated || rows.truncated,
            ),
        )
    }.flowOn(dispatchers.io)

    /**
     * Splits the head pass into what it already proved and what still has to be read in full.
     *
     * The bucket key is **`(size, head)` and not the head alone**: a truncated copy shares its
     * original's first 64 KiB, and keying on the digest alone would put two files of different
     * lengths in one group and offer to delete the longer one.
     *
     * A file no larger than the window was read whole, so its head digest **is** its full digest and
     * a second read of it would be pure waste — that is the property `FileDigest.digest` promises.
     */
    private fun DigestPass.partitionByHead(): ProvenAndUnproven {
        val proven = LinkedHashMap<String, List<ScannedFile>>()
        val unproven = ArrayList<ScannedFile>()
        for ((key, pairs) in digests.groupBy { (file, hex) -> file.sizeBytes to hex }) {
            if (pairs.size < 2) continue
            val (sizeBytes, hex) = key
            if (sizeBytes <= FileDigest.HEAD_BYTES) {
                proven[hex] = pairs.map { it.first }
            } else {
                unproven += pairs.map { it.first }
            }
        }
        return ProvenAndUnproven(proven, unproven)
    }

    /** Groups the head pass already settled, and the files that still need a full read. */
    private data class ProvenAndUnproven(
        val proven: LinkedHashMap<String, List<ScannedFile>>,
        val unproven: List<ScannedFile>,
    )

    /** At most one `Hashing` emission per [HashProgressEvery] files, whatever the chunk size is. */
    private fun FlowCollector<DuplicateScanProgress>.hashingProgress(
        total: Int,
    ): suspend (Int) -> Unit {
        var lastEmitted = 0
        return { hashed ->
            if (hashed - lastEmitted >= HashProgressEvery) {
                lastEmitted = hashed
                emit(DuplicateScanProgress.Hashing(hashed, total))
            }
        }
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
         * reported as success. Ninety seconds is what a full-volume two-phase pass needs on a loaded
         * device; `DuplicatesViewModel`'s own timeout sits above it so this one always wins and the
         * partial answer is the one that reaches the screen.
         */
        val ScanBudget = 90.seconds

        /** The walk's share of [ScanBudget]. The digest passes get whatever is left. */
        val CollectBudget = 30.seconds

        /**
         * §2.2's figure. One storage device: a wider fan-out queues at the same head, and the
         * competitor's `xc.j.a()` fans out one coroutine per directory to prove it.
         */
        const val DigestParallelism = 4

        /**
         * UNKNOWN — no source states a depth. Sixteen matches `JunkWalkBounds.APK_WALK_MAX_DEPTH`,
         * the other pass in the app that walks whole volumes rather than one named folder.
         */
        const val MaxDepth = 16

        /** A zero-byte file matches every other zero-byte file; nothing above that is filtered. */
        const val MinCandidateBytes = 1L

        const val CollectProgressEvery = 256
        const val HashProgressEvery = 32
    }
}
