package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import kotlinx.coroutines.flow.Flow

/**
 * The two flags of `od.g0`, over DataStore
 * (`docs/screens/17-notification-and-permissions.md` §0.2, §5.1).
 *
 * [observe] emits the master switch and the per-app rows together, so a screen opens **one** collector
 * and the two halves can never be a frame apart (§2.2). Building the row list means joining the
 * DataStore flag set with `InstalledAppsRepository`; that join lives in the implementation, so no
 * ViewModel ever holds two upstreams it has to keep in step.
 *
 * Every write is a suspending DataStore `edit {}`. The competitor's is a `commit()` on the main thread,
 * one synchronous fsync per switch tap (§2.5).
 *
 * **[setMasterEnabled] is called only by the master switch.** Turning off the last enabled app must not
 * silently write the master flag — that is a per-app action mutating a global setting with no undo, and
 * in the competitor it also makes the whole list vanish (§2.5).
 */
interface NotificationHidingSettingsStore {

    fun observe(): Flow<NotificationHidingSettings>

    suspend fun setMasterEnabled(enabled: Boolean): AppResult<Unit>

    suspend fun setAppEnabled(packageName: String, enabled: Boolean): AppResult<Unit>
}
