package com.pion.phonecleaner.data.notification

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.data.datastore.NotificationPrefs
import com.pion.phonecleaner.domain.repository.FeatureStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * `od.d0`'s `sens_app_count`, over the one DataStore
 * (`docs/screens/17-notification-and-permissions.md` §0.2).
 *
 * DECLARED IN `notificationDataModule`.
 *
 * The competitor writes this counter **only in `onDestroy`**, so a process killed while the Permission
 * Manager is open never updates a number three out-of-app surfaces read (§4.5). Here the write happens
 * at the end of every successful scan and every successful resume-refresh.
 *
 * The write swallows its failure by design: it is bookkeeping the user did not ask for, and the caller
 * is inside a `launchSafely` whose failure path belongs to the scan, not to the counter.
 */
internal class DataStoreFeatureStats(
    private val dataStore: DataStore<Preferences>,
    private val log: AppLogger,
) : FeatureStatsRepository {

    override suspend fun setSensitiveAppCount(count: Int) {
        runCatching { dataStore.edit { it[NotificationPrefs.SENSITIVE_APP_COUNT] = count } }
            .onFailure { log.e(it) { "Sensitive app count not written" } }
    }

    override fun observeSensitiveAppCount(): Flow<Int?> =
        dataStore.data.map { it[NotificationPrefs.SENSITIVE_APP_COUNT] }.distinctUntilChanged()
}
