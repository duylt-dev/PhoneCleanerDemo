package com.pion.phonecleaner.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pion.phonecleaner.data.database.entity.HiddenNotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Reads and writes `hidden_notifications`. Declared `single` in `coreDataModule`
 * (`docs/screens/17-notification-and-permissions.md:718`), never in the notification cluster's module.
 *
 * **Room is the notification.** `LiveEventBus("flux_refresh_noti_list")` is deleted
 * (`docs/system-architecture.md` §5.10): the list screen and the id-900 summary both collect
 * [observeAll] / [observeCount], so a row inserted from the system binder thread reaches every reader
 * without a broadcast, and a full disk re-read per posted notification stops being a thing that can
 * happen (`docs/screens/17-notification-and-permissions.md` §3.5).
 */
@Dao
interface HiddenNotificationDao {

    /** Reverse-chronological, flat — kept flat for parity; grouping is recorded as a follow-up. */
    @Query("SELECT * FROM hidden_notifications ORDER BY posted_at DESC")
    fun observeAll(): Flow<List<HiddenNotificationEntity>>

    /** The badge count. The competitor reads it with a main-thread Gson parse (`od.i.o()`). */
    @Query("SELECT COUNT(*) FROM hidden_notifications")
    fun observeCount(): Flow<Int>

    /**
     * REPLACE, because the platform re-posts an updated notification under the same key: an updated
     * notification must update its row, not be silently dropped by an ABORT.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notification: HiddenNotificationEntity)

    /** Returns rows removed, so "restore" can tell a real dismissal from a no-op. */
    @Query("DELETE FROM hidden_notifications WHERE `key` = :key")
    suspend fun deleteByKey(key: String): Int

    /** Returns rows removed — the count the clear-all animation renders, durably. */
    @Query("DELETE FROM hidden_notifications")
    suspend fun clearAll(): Int

    /**
     * Keeps the [keep] newest rows and deletes the rest. This is the competitor's `size == 100` cap
     * written as `> MAX`: with `==`, a list that ever exceeded 100 is never trimmed again
     * (`docs/screens/17-notification-and-permissions.md` §3.5).
     */
    @Query(
        "DELETE FROM hidden_notifications WHERE `key` NOT IN " +
            "(SELECT `key` FROM hidden_notifications ORDER BY posted_at DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int): Int
}
