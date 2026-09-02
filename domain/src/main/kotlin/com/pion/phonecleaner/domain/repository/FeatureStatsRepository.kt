package com.pion.phonecleaner.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * The one counter three out-of-app surfaces read: how many installed apps hold at least one sensitive
 * permission (`docs/screens/17-notification-and-permissions.md` §0.2, ports `od.d0`'s `sens_app_count`).
 *
 * **It is written at the end of every successful scan and every successful resume-refresh, never in a
 * destroy callback.** The competitor writes it only in `onDestroy`, so a process killed while the
 * screen is open never updates it (§4.5).
 *
 * The write does not return `AppResult`: it is bookkeeping the user did not ask for, fired from inside
 * a `launchSafely` that already owns the failure path, exactly like `MarkFeatureUsedUseCase`.
 */
interface FeatureStatsRepository {

    suspend fun setSensitiveAppCount(count: Int)

    /** For the surfaces that render the count. `null` until a scan has ever finished. */
    fun observeSensitiveAppCount(): Flow<Int?>
}
