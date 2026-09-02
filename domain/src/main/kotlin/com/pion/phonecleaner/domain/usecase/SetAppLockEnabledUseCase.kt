package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.AppLockSettingsRepository

/**
 * The master switch (`docs/screens/16-app-lock.md` §4.2).
 *
 * It writes a preference and returns. **It does not start or stop `ForegroundAppMonitor`** — the
 * monitor observes the persisted flag, so the flag is the only truth. `MajimatActivity.java:37-44`
 * calls `od.e0.d()` / `g()` straight from the click listener, which puts the watchdog's lifetime in
 * a view callback and leaves it out of step with any other writer of the same preference.
 *
 * Registered `factoryOf(::SetAppLockEnabledUseCase)` in `domainModule`.
 */
class SetAppLockEnabledUseCase(
    private val settings: AppLockSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean): AppResult<Unit> =
        settings.setAppLockEnabled(enabled)
}
