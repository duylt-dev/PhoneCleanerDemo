package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.applock.LockableApp
import com.pion.phonecleaner.domain.repository.AppLockRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * Every launcher app with its current lock state (`docs/screens/16-app-lock.md` §1.2).
 *
 * A `Flow`, not a one-shot read: `Chaennia.d()` is called exactly once when the screen opens, so an
 * app installed while the screen is open never appears
 * (`docs/reverse-engineering/16-app-lock.md` §4.4).
 *
 * Registered `factoryOf(::ObserveLockableAppsUseCase)` in `domainModule`.
 */
class ObserveLockableAppsUseCase(
    private val appLock: AppLockRepository,
) {
    operator fun invoke(): Flow<ImmutableList<LockableApp>> = appLock.observeLockableApps()
}
