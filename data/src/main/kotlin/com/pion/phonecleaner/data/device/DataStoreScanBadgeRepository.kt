package com.pion.phonecleaner.data.device

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.domain.repository.ScanBadgeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/**
 * The once-a-day red dot on the home Running Apps tile
 * (`docs/screens/18-device-battery-and-apps.md` §6.4).
 *
 * The two ported defects are both in the storage:
 *
 * 1. **The day is local, not GMT.** [AppClock] is injected so "today" is pinnable in a test, which a
 *    hard-coded `System.currentTimeMillis()` is not — and the timezone bug is exactly the kind that
 *    only a pinned clock finds.
 * 2. **The write is not in the navigation path.** `markScannedToday` is called from its own
 *    `launchSafely` after the scan finishes; the competitor `commit()`s on the calling thread between
 *    the ad callback and `startActivity`.
 *
 * The badge is purely cosmetic in the competitor too: it does not block navigation, does not skip the
 * scan and does not change what the scan does. Nothing here gives it more authority than that.
 */
internal class DataStoreScanBadgeRepository(
    private val store: DataStore<Preferences>,
    private val clock: AppClock,
) : ScanBadgeRepository {

    override fun observeRunningAppsBadge(): Flow<Boolean> = store.data
        .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
        .map { prefs ->
            // Never scanned reads as "not today", which is what shows the dot on a fresh install.
            prefs[DeviceScanPrefs.RUNNING_APPS_LAST_SCAN_EPOCH_DAY] != todayEpochDay()
        }
        .distinctUntilChanged()

    override suspend fun markScannedToday(): AppResult<Unit> = try {
        store.edit { it[DeviceScanPrefs.RUNNING_APPS_LAST_SCAN_EPOCH_DAY] = todayEpochDay() }
        Unit.asSuccess()
    } catch (e: IOException) {
        AppError.Storage(cause = e.message).asFailure()
    }

    /**
     * `Int`, not a formatted string: an epoch day cannot be parsed back wrong, and it sorts. The
     * range is safe for roughly 5.8 million years either side of 1970.
     */
    private fun todayEpochDay(): Int = Instant.ofEpochMilli(clock.now().toEpochMilliseconds())
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .toEpochDay()
        .toInt()
}
