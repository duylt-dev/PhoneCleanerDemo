package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.applock.AppLockSettings
import kotlinx.coroutines.flow.Flow

/**
 * The two App Lock preferences, replacing the competitor's `app_lock_enable` / `app_lock_new_app`
 * keys in `od.d0` (`docs/screens/16-app-lock.md` §1.4).
 *
 * DECLARED IN `appLockDataModule`. No other cluster names this type.
 *
 * ### Starting and stopping the monitor is this repository's job
 *
 * `docs/screens/16-app-lock.md` §4.2: the ViewModel calls [setAppLockEnabled] and nothing else.
 * `MajimatActivity.java:37-44` calls `od.e0.d()` / `g()` straight from a click listener, so the
 * watchdog's lifetime is decided in a view callback and any other writer of the same preference
 * leaves it out of step. Here the flag is the truth and `ForegroundAppMonitor` observes it, which
 * also means a change made anywhere — including by `ClearAppLockUseCase` — reaches the monitor.
 *
 * Writes suspend, on `dispatchers.io` inside the implementation.
 */
interface AppLockSettingsRepository {

    /** One upstream carrying both flags, so the two switches can never be a frame out of step. */
    fun observeSettings(): Flow<AppLockSettings>

    /** The master switch. Everything App Lock does is gated on it. */
    suspend fun setAppLockEnabled(enabled: Boolean): AppResult<Unit>

    /** Whether a newly installed app is offered for locking. See `AppLockSettings` for the caveat. */
    suspend fun setLockNewlyInstalled(enabled: Boolean): AppResult<Unit>
}
