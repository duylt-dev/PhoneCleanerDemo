package com.pion.phonecleaner.data.trash

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.data.database.TrashEntryDao
import com.pion.phonecleaner.data.database.entity.TrashEntryEntity
import com.pion.phonecleaner.data.database.entity.TrashRowState
import java.io.File

/**
 * The startup sweep: settles whatever a process kill left half-written, by asking the filesystem
 * which side of a move actually happened — never by replaying a caller's half-finished call
 * (`TrashRepository.reconcile`'s own KDoc).
 *
 * Runs from two places, both cheap: `TrashPurgeWorker` (Phase 06) before it purges, and the trash
 * screen's `onScreenStarted` (Phase 05), so a list a user opens can never show a half-written row.
 */
internal class TrashReconciler(
    private val dao: TrashEntryDao,
    private val log: AppLogger,
    private val roots: TrashRoots,
) {

    suspend fun reconcile(): Int {
        var settled = 0
        for (row in dao.unsettled()) {
            if (settleUnsettledRow(row)) settled++
        }
        for (row in dao.allTrashed()) {
            // TRASHED but the file is gone: a file manager wiped it out from under us. Self-heal.
            if (roots.canInspect(row.trashedPath) && !File(row.trashedPath).exists()) {
                dao.deleteById(row.id)
                settled++
            }
        }
        return settled
    }

    /** One row of the reconcile table. Every branch either promotes or deletes — never leaves the row as it was. */
    private suspend fun settleUnsettledRow(row: TrashEntryEntity): Boolean {
        if (!roots.canInspect(row.trashedPath)) return false
        val atTrashed = File(row.trashedPath).exists()
        val atOriginal = File(row.originalPath).exists()
        return when (TrashRowState.entries.firstOrNull { it.name == row.state }) {
            TrashRowState.PENDING -> when {
                atTrashed -> promote(row.id)
                atOriginal -> drop(row.id) // the move never happened
                else -> drop(row.id, "PENDING row ${row.id} has no file at either path")
            }

            TrashRowState.RESTORING -> when {
                // The original may be an unrelated newer file. Keep our payload until it moved.
                atTrashed -> promote(row.id)
                atOriginal -> drop(row.id) // the restore completed
                else -> drop(row.id, "RESTORING row ${row.id} has no file at either path")
            }

            TrashRowState.PURGING -> when {
                // Never finish a destructive action nobody re-confirmed; the next expiry sweep will
                // take it anyway if it is still due.
                atTrashed -> promote(row.id)
                else -> drop(row.id)
            }

            // unsettled() never returns TRASHED rows; null means a state this build does not know —
            // left alone rather than guessed at.
            TrashRowState.TRASHED, null -> false
        }
    }

    private suspend fun promote(id: String): Boolean {
        dao.setState(id, TrashRowState.TRASHED.name)
        return true
    }

    private suspend fun drop(id: String, warning: String? = null): Boolean {
        if (warning != null) log.e { warning }
        dao.deleteById(id)
        return true
    }
}
