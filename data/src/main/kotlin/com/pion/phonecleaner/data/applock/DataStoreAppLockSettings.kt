package com.pion.phonecleaner.data.applock

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.datastore.AppLockPrefs
import com.pion.phonecleaner.domain.model.applock.AppLockSettings
import com.pion.phonecleaner.domain.repository.AppLockSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The two App Lock preferences, replacing `od.d0`'s `app_lock_enable` / `app_lock_new_app`.
 *
 * Three things change from the competitor, and each one is a defect it has:
 *
 * 1. **One upstream.** Both flags come off one `Flow`, so the settings screen's two switches cannot
 *    be a frame out of step and the App Lock home reads the same value the settings page wrote.
 * 2. **The write suspends.** `od.d0.k`/`l` is `edit().putX().commit()` on the calling thread
 *    (`java/od/d0.java:78-81`) — a synchronous fsync on the UI thread per toggle. Here it is a
 *    DataStore `edit {}` on `dispatchers.io`, chosen inside this class (`LLM.md` §6.5).
 * 3. **Nothing here touches the monitor.** `MajimatActivity.java:37-44` calls `od.e0.d()` / `g()`
 *    from the click listener; `ForegroundAppMonitor` observes [observeSettings] instead, so the
 *    persisted flag is the only truth and every writer reaches the monitor by writing it.
 *
 * DECLARED IN `appLockDataModule`.
 */
internal class DataStoreAppLockSettings(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : AppLockSettingsRepository {

    override fun observeSettings(): Flow<AppLockSettings> =
        dataStore.data
            .map { prefs ->
                // Both default to `true`, for parity with the competitor's own defaults
                // (docs/screens/16-app-lock.md §4.5) — but read off the main thread.
                AppLockSettings(
                    isAppLockEnabled = prefs[AppLockPrefs.ENABLED] ?: DEFAULT_ENABLED,
                    lockNewlyInstalled = prefs[AppLockPrefs.LOCK_NEW_APPS] ?: DEFAULT_LOCK_NEW_APPS,
                )
            }
            .distinctUntilChanged()

    override suspend fun setAppLockEnabled(enabled: Boolean): AppResult<Unit> =
        write("setAppLockEnabled") { it[AppLockPrefs.ENABLED] = enabled }

    override suspend fun setLockNewlyInstalled(enabled: Boolean): AppResult<Unit> =
        write("setLockNewlyInstalled") { it[AppLockPrefs.LOCK_NEW_APPS] = enabled }

    private suspend fun write(
        what: String,
        block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching { dataStore.edit(block) }.fold(
            onSuccess = { AppResult.Success(Unit) },
            onFailure = {
                log.e(it) { "App Lock $what failed" }
                AppResult.Failure(AppError.Unexpected(it.message))
            },
        )
    }

    private companion object {
        const val DEFAULT_ENABLED = true
        const val DEFAULT_LOCK_NEW_APPS = true
    }
}
