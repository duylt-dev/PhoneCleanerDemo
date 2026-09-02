package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.notification.HiddenNotification
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The hidden-notification store — the half of `od.i` that persists
 * (`docs/screens/17-notification-and-permissions.md` §0.2, §5.1).
 *
 * Room-backed, over the `hidden_notifications` table, whose DAO is declared in `coreDataModule`
 * (§3.4). **Room is the notification**: `LiveEventBus("flux_refresh_noti_list")` is deleted, because
 * a `Flow` from the table already tells every reader that a row arrived. The competitor's channel
 * carries no payload, so each event costs a full disk re-read plus N icon loads (§3.5).
 *
 * Only the write half is declared as `AppResult`: a read is a `Flow` that simply stops emitting if the
 * database cannot be opened, and every collector goes through `collectSafely`.
 */
interface NotificationCleanerRepository {

    /** Reverse-chronological and flat. Grouping by package is a recorded follow-up, not smuggled in. */
    fun observeHidden(): Flow<ImmutableList<HiddenNotification>>

    /** The badge count for the id-900 summary. `od.i.o()` computes it with a main-thread Gson parse. */
    fun observeHiddenCount(): Flow<Int>

    /**
     * Stores one intercepted notification and trims the table back to the cap.
     *
     * The competitor's `od.i.B()` does a full Gson decode, an insert, a full encode and a `commit()`
     * on the system binder thread, once per posted notification, and its cap guard is `size == 100` —
     * so a list that ever exceeded 100 is never trimmed again (§5.2).
     */
    suspend fun insert(notification: HiddenNotification): AppResult<Unit>

    /** Removes one row. `DELETE WHERE key = :key`, and [HiddenNotification.key] is non-null. */
    suspend fun dismiss(key: String): AppResult<Unit>

    /** Wipes the table and returns how many rows went, so the clear animation renders a real count. */
    suspend fun clearAll(): AppResult<Int>
}
