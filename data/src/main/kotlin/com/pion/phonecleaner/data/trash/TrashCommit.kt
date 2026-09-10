package com.pion.phonecleaner.data.trash

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.data.database.TrashEntryDao
import com.pion.phonecleaner.data.database.entity.TrashRowState
import com.pion.phonecleaner.data.work.TrashPurgeWorker
import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** One file, or one already-measured directory tree, about to become a row. */
internal data class CommitRequest(
    val id: String,
    val originalPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val isDirectory: Boolean,
    val mimeType: String?,
    val source: FeatureId,
    val originContentUri: String?,
    val batchId: String? = null,
)

/**
 * The two-phase-commit insert `TrashRepository`'s own KDoc requires: INSERT `PENDING` -> physical move
 * -> UPDATE `TRASHED`, or delete the row if the move failed (T7, `plan.md`'s risk table). Every step
 * runs inside `NonCancellable` — a cancel lands BETWEEN items in a batch, never inside one, so what
 * moved always has a committed row.
 */
internal class TrashCommit(
    private val dao: TrashEntryDao,
    private val mover: TrashMover,
    private val clock: AppClock,
    private val log: AppLogger,
    private val context: Context,
    private val schedulePurge: (() -> Unit)? = null,
) {

    /** The trashed path on success, or null on a refused move — the caller reports [request]'s path as failed. */
    suspend fun commit(request: CommitRequest): String? = withContext(NonCancellable) {
        val destination = mover.resolveDestination(request.originalPath, request.id) ?: return@withContext null
        val row = newPendingTrashEntry(
            id = request.id,
            originalPath = request.originalPath,
            trashedPath = destination.absolutePath,
            displayName = request.displayName,
            sizeBytes = request.sizeBytes,
            fileCount = request.fileCount,
            isDirectory = request.isDirectory,
            mimeType = request.mimeType,
            source = request.source,
            originContentUri = request.originContentUri,
            batchId = request.batchId,
            trashedAt = clock.now(),
        )
        dao.insert(row) // 1. the row exists before the file moves

        when (val moved = mover.moveIn(request.originalPath, destination)) {
            is MoveResult.Moved -> {
                if (request.originContentUri != null) {
                    val cleared = mover.clearMediaRow(request.originContentUri, request.originalPath)
                    if (!cleared) {
                        log.e { "Stale media row survived for ${request.originContentUri}" }
                        dao.setMediaRowCleared(request.id, cleared = false)
                    }
                }
                dao.setState(request.id, TrashRowState.TRASHED.name) // 2. commit
                // Scheduling cannot turn an already committed move into a reported move failure.
                runCatching { schedulePurge?.invoke() ?: scheduleFirstPurgeWorkerIfNeeded() }
                    .onFailure { log.e(it) { "Could not schedule trash purge" } }
                moved.trashedPath
            }

            MoveResult.Refused -> {
                dao.deleteById(request.id) // 3. no row, no file moved — request.originalPath -> failedPaths
                null
            }
        }
    }

    /**
     * Enqueued here rather than in `App.onCreate`: an empty bin needs no periodic job, and
     * `App.onCreate` is licensed to do two things only — set the log gate and start Koin (`LLM.md`
     * §6.2). `KEEP` makes this idempotent, so calling it after every successful move — not only the
     * very first — costs one WorkManager lookup and nothing else.
     */
    private fun scheduleFirstPurgeWorkerIfNeeded() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TrashPurgeWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            TrashPurgeWorker.request(),
        )
    }
}
