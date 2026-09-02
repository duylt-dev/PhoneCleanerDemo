package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.FileScanProgress
import com.pion.phonecleaner.domain.model.file.ScanCoverage
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Everything over [THRESHOLD_BYTES], from every surface the current grant reaches
 * (`docs/screens/14-file-tools-and-app-manager.md` §1.2).
 *
 * **One threshold constant.** The competitor writes `10485760` four times in one file — four places
 * to disagree.
 *
 * **The cap is applied here, not in the screen.** A `LazyColumn` rendering 200 of 5 000 still retains
 * all 5 000 (`LLM.md` §8).
 *
 * The corpus this scans changes with the storage branch and the threshold does not (§10 item 6): in
 * the default branch a big file outside the three media collections is visible only through a SAF
 * tree the user granted, which is why [FileScanProgress.Finished] carries the coverage.
 */
class ScanBigFilesUseCase(
    private val scanner: StorageScanner,
    private val mediaStore: MediaStoreRepository,
    private val roots: StorageRootProvider,
    private val permissions: PermissionRepository,
) {

    operator fun invoke(): Flow<FileScanProgress> = flow {
        val found = LinkedHashMap<String, ScannedFile>()
        var scanned = 0

        // Strategy 3 first: the three media collections are the default branch's main surface, and
        // they answer in one query each instead of a walk.
        for (result in listOf(mediaStore.images(), mediaStore.videos(), mediaStore.audio())) {
            val rows = (result as? AppResult.Success)?.value ?: continue
            for (row in rows) {
                scanned++
                if (row.sizeBytes >= THRESHOLD_BYTES) found.putIfAbsent(row.path, row)
            }
        }
        emit(FileScanProgress.Scanning(scanned))

        // Strategy 4: whatever else is walkable — our own directories, plus the SAF trees the user
        // granted. `StorageRootProvider` is the only object that knows which those are (§8.4).
        val walkable = (roots.readableRoots() as? AppResult.Success)?.value ?: persistentListOf()
        if (walkable.isNotEmpty()) {
            scanner.walk(WalkConfig(roots = walkable, maxDepth = MAX_DEPTH)).collect { file ->
                scanned++
                if (file.sizeBytes >= THRESHOLD_BYTES) found.putIfAbsent(file.path, file)
                if (scanned % PROGRESS_EVERY == 0) emit(FileScanProgress.Scanning(scanned))
            }
        }

        emit(
            FileScanProgress.Finished(
                files = found.values.sortedByDescending(ScannedFile::sizeBytes)
                    .take(MAX_RESULTS)
                    .toImmutableList(),
                coverage = coverage(),
            ),
        )
    }

    /**
     * `coveredSurfaces()` answers *what* was read, never *whether that was all*, so the partial flag
     * is decided by the one domain-visible predicate that does answer it: all-files access. It is
     * **never assumed grantable** (`docs/system-architecture.md` §8.1) — on the default branch this is
     * therefore always partial, which is the honest reading, and the screen offers a folder picker
     * rather than implying the list is complete.
     */
    private suspend fun coverage(): ScanCoverage = ScanCoverage(
        surfaces = (roots.coveredSurfaces() as? AppResult.Success)?.value ?: persistentListOf(),
        isPartial = !permissions.isGranted(AppPermission.AllFiles),
    )

    companion object {
        /** 10 MiB. The competitor's four copies of `10485760`, written once. */
        const val THRESHOLD_BYTES: Long = 10L * 1024L * 1024L

        /** Capped in the use case, never in the screen (§1.2). */
        const val MAX_RESULTS: Int = 200

        /**
         * UNKNOWN — no source states a depth. `WalkConfig.maxDepth` is required and has no default
         * precisely so a caller must state one; looked in `docs/system-architecture.md` §4.5 and in
         * this appendix's §0.1 and §1.2, neither of which gives a number. Ten levels reaches every
         * ordinary media tree while bounding a pathological one.
         */
        private const val MAX_DEPTH: Int = 10

        private const val PROGRESS_EVERY: Int = 64
    }
}
