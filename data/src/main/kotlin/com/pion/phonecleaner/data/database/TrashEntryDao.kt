package com.pion.phonecleaner.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pion.phonecleaner.data.database.entity.TrashEntryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes `trash_entries`. Declared `single` in `coreDataModule`, beside the other two DAOs —
 * shared infrastructure, never in `trashDataModule` (`LLM.md` §6.4).
 */
@Dao
interface TrashEntryDao {

    /** Settled rows only, newest first, capped — the DAO caps what a screen can ever be handed. */
    @Query(
        "SELECT * FROM trash_entries WHERE state = 'TRASHED' ORDER BY trashed_at DESC LIMIT $MAX_OBSERVED_ENTRIES",
    )
    fun observeTrashed(): Flow<List<TrashEntryEntity>>

    /** For the Settings row and the home tile badge, without loading [observeTrashed]'s full list. */
    @Query(
        "SELECT COUNT(*) AS entryCount, COALESCE(SUM(size_bytes), 0) AS totalBytes " +
            "FROM trash_entries WHERE state = 'TRASHED'",
    )
    fun observeSummary(): Flow<TrashSummaryRow>

    @Query("SELECT * FROM trash_entries WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<TrashEntryEntity>

    /** Everything whose stored `expires_at` has passed. Never "older than N" measured at read time. */
    @Query("SELECT * FROM trash_entries WHERE state = 'TRASHED' AND expires_at <= :nowEpochMillis")
    suspend fun expired(nowEpochMillis: Long): List<TrashEntryEntity>

    /** Anything not `TRASHED` — what `TrashReconciler` settles on start. */
    @Query("SELECT * FROM trash_entries WHERE state != 'TRASHED'")
    suspend fun unsettled(): List<TrashEntryEntity>

    /**
     * Every settled row, uncapped — unlike [observeTrashed]. `TrashReconciler`'s self-heal pass (a
     * file manager wiped a trashed folder out from under a `TRASHED` row) has to see all of them, not
     * only the newest 500 a screen would render.
     */
    @Query("SELECT * FROM trash_entries WHERE state = 'TRASHED'")
    suspend fun allTrashed(): List<TrashEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: TrashEntryEntity)

    /** Returns rows updated, so a caller can tell a real transition from a row that vanished under it. */
    @Query("UPDATE trash_entries SET state = :state WHERE id = :id")
    suspend fun setState(id: String, state: String): Int

    @Query("UPDATE trash_entries SET media_row_cleared = :cleared WHERE id = :id")
    suspend fun setMediaRowCleared(id: String, cleared: Boolean): Int

    @Query("UPDATE trash_entries SET size_bytes = :bytes, state = 'TRASHED' WHERE id = :id")
    suspend fun updateRemaining(id: String, bytes: Long): Int

    /** Returns rows removed. */
    @Query("DELETE FROM trash_entries WHERE id = :id")
    suspend fun deleteById(id: String): Int

    private companion object {
        /**
         * Caps what [observeTrashed] ever hands the screen. A junk sweep can trash thousands of small
         * files; the list is capped here rather than only in the ViewModel because a Room `Flow` with
         * no `LIMIT` re-reads and re-diffs the whole table on every write, not only what the UI renders.
         */
        const val MAX_OBSERVED_ENTRIES = 500
    }
}

/**
 * [TrashSummaryRow.entryCount]/[TrashSummaryRow.totalBytes] deliberately share `TrashSummary`'s own
 * field names: the SQL aliases (`AS entryCount`, `AS totalBytes`) are what let Room bind this POJO with
 * no `@ColumnInfo`, and `data/trash/TrashEntryMapping.kt`'s `toModel()` is then a 1:1 copy.
 */
data class TrashSummaryRow(val entryCount: Int, val totalBytes: Long)
