package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.AppLockRepository

/**
 * Locks or unlocks one app (`docs/screens/16-app-lock.md` §1.2).
 *
 * **Nothing here restarts the monitor.** `SemantanActivity` calls `od.e0.d()` after every lock and
 * every unlock (`:209`, `:357`) because the watchdog holds a stale snapshot of the list;
 * `ForegroundAppMonitor` observes `AppLockRepository.lockedPackages()` instead, so the write *is*
 * the notification.
 *
 * Registered `factoryOf(::SetAppLockedUseCase)` in `domainModule`.
 */
class SetAppLockedUseCase(
    private val appLock: AppLockRepository,
) {
    suspend operator fun invoke(packageName: String, locked: Boolean): AppResult<Unit> =
        appLock.setLocked(packageName, locked)
}
