package com.pion.phonecleaner.data.trash

import com.pion.phonecleaner.data.database.TrashEntryDao
import com.pion.phonecleaner.data.database.entity.TrashEntryEntity
import com.pion.phonecleaner.data.database.entity.TrashRowState
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Reverse transitions run under RoomTrashRepository's mutex, with each row settled before cancellation. */
internal class TrashRemoval(private val dao: TrashEntryDao, private val mover: TrashMover) {
    suspend fun restore(ids: List<String>): TrashRestoreOutcome {
        val rows = dao.byIds(ids).associateBy { it.id }
        val restored = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var renamed = 0
        for (id in ids) {
            currentCoroutineContext().ensureActive()
            val row = rows[id]
            if (row == null || row.state != TrashRowState.TRASHED.name) {
                failed += id
                continue
            }
            withContext(NonCancellable) {
                dao.setState(id, TrashRowState.RESTORING.name)
                when (val result = mover.moveOut(row.trashedPath, row.originalPath)) {
                    is RestoreResult.Restored -> {
                        dao.deleteById(id)
                        mover.registerRestored(result.path, row.mimeType)
                        restored += id
                        if (result.renamed) renamed++
                    }
                    RestoreResult.Failed -> {
                        dao.setState(id, TrashRowState.TRASHED.name)
                        failed += id
                    }
                }
            }
        }
        return TrashRestoreOutcome(restored.toImmutableList(), failed.toImmutableList(), renamed)
    }

    suspend fun purge(rows: List<TrashEntryEntity>): TrashPurgeOutcome {
        val purged = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var freed = 0L
        for (row in rows) {
            currentCoroutineContext().ensureActive()
            if (row.state != TrashRowState.TRASHED.name) {
                failed += row.id
                continue
            }
            withContext(NonCancellable) {
                dao.setState(row.id, TrashRowState.PURGING.name)
                val result = mover.deletePermanently(row.trashedPath)
                freed += result.freedBytes
                if (result.removed) {
                    dao.deleteById(row.id)
                    purged += row.id
                } else {
                    // Partial directory deletion keeps the row and credits only removed bytes.
                    dao.updateRemaining(row.id, (row.sizeBytes - result.freedBytes).coerceAtLeast(0L))
                    failed += row.id
                }
            }
        }
        return TrashPurgeOutcome(purged.toImmutableList(), freed, failed.toImmutableList())
    }
}
