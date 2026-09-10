package com.pion.phonecleaner.data.files

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.FlowCollector
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Stages 1–3 of `docs/screens/14-file-tools-and-app-manager.md` §2.2 — everything before the first
 * byte is hashed. Split out of `Md5DuplicateFinder` so neither file passes the size rule
 * (`.claude/rules/development-rules.md`) and so the branch below can be read on its own.
 */

/** Stage 1's output, plus whether the walk ran out of time before the tree ran out. */
internal data class CollectedRows(val files: List<ScannedFile>, val truncated: Boolean)

/**
 * Stage 1. The branch is chosen from `AppPermission.AllFiles` and from nothing else, so no screen,
 * ViewModel or use case learns which one ran (`docs/system-architecture.md` §8.4). It is **checked,
 * never assumed** (§8.1): without the grant this yields exactly the corpus the screen had before —
 * the three media collections.
 *
 * **The walk replaces the media queries rather than joining them.** Both would see the same photo
 * under two different ids — a filesystem path and a `content://` row — and a file may not be offered
 * as a duplicate of itself. With the grant held, the walk already covers every media file anyway.
 *
 * `walkFilesBounded` returns silently when its own `timeLimit` expires, so the elapsed time is the
 * only signal that the corpus is partial; a partial corpus reported as complete is the defect §2.2
 * exists to remove.
 */
internal suspend fun FlowCollector<DuplicateScanProgress>.collectRows(
    mediaStore: MediaStoreRepository,
    scanner: StorageScanner,
    roots: StorageRootProvider,
    permissions: PermissionRepository,
    maxDepth: Int,
    budget: Duration,
    progressEvery: Int,
): CollectedRows {
    val rows = LinkedHashMap<String, ScannedFile>()
    emit(DuplicateScanProgress.Collecting(found = 0))

    val walkable = if (permissions.isGranted(AppPermission.AllFiles)) {
        (roots.readableRoots() as? AppResult.Success)?.value ?: persistentListOf()
    } else {
        persistentListOf()
    }

    if (walkable.isNotEmpty()) {
        val startedAt = TimeSource.Monotonic.markNow()
        scanner.walk(WalkConfig(roots = walkable, maxDepth = maxDepth, timeLimit = budget))
            .collect { file ->
                rows.putIfAbsent(file.id, file)
                if (rows.size % progressEvery == 0) {
                    emit(DuplicateScanProgress.Collecting(rows.size))
                }
            }
        return CollectedRows(rows.values.toList(), truncated = startedAt.elapsedNow() >= budget)
    }

    for (result in listOf(mediaStore.images(), mediaStore.videos(), mediaStore.audio())) {
        (result as? AppResult.Success)?.value?.forEach { rows.putIfAbsent(it.id, it) }
        emit(DuplicateScanProgress.Collecting(rows.size))
    }
    return CollectedRows(rows.values.toList(), truncated = false)
}

/**
 * Stages 2–3. **Deduped by [ScannedFile.id], not by `path`** — on API 29+ a MediaStore row's `path`
 * is `RELATIVE_PATH`, a *folder*, so a path-keyed map collapses every file in a folder to one
 * candidate and the screen reports "no duplicates" on any modern device. The id is the `content://`
 * URI for a MediaStore row and the absolute path for a walked file: unique in both branches.
 *
 * Then the exact-byte-size pre-filter the competitor has no equivalent of. Only a zero-byte file is
 * excluded on size: it matches every other zero-byte file and is not a duplicate worth listing.
 * Nothing above that is filtered — a duplicate `.pdf` of 3 KiB is still a duplicate.
 */
internal fun sizeCandidates(rows: CollectedRows, minBytes: Long): List<ScannedFile> = rows.files
    .filter { it.sizeBytes >= minBytes }
    .groupBy { it.sizeBytes }
    .values
    .filter { it.size > 1 }
    .flatten()
