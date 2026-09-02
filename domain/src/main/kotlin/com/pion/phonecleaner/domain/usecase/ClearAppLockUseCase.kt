package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.AppLockPinRepository
import com.pion.phonecleaner.domain.repository.AppLockRepository

/**
 * Wipes the PIN, its salt, the lockout record **and** the lock list (`docs/screens/16-app-lock.md`
 * §4.2).
 *
 * This row is **new** — the competitor has no recovery of any kind, so a forgotten PIN means
 * clearing app data (`docs/reverse-engineering/16-app-lock.md` §3.2). It is the only honest answer
 * once the PIN is irreversibly hashed, and the confirmation must say that the locked-app list goes
 * with it *before* it runs (§4.5).
 *
 * The two wipes are sequenced PIN-first on purpose: if the second call fails, what is left is a
 * device with a lock list and no PIN, which `AppLockPinRepository.isPinSet` reports honestly and the
 * gate answers by sending the user to `PinMode.Set`. The other order would leave a PIN guarding
 * nothing, which reads as success.
 *
 * Registered `factoryOf(::ClearAppLockUseCase)` in `domainModule`.
 */
class ClearAppLockUseCase(
    private val pin: AppLockPinRepository,
    private val appLock: AppLockRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = when (val cleared = pin.clearPin()) {
        is AppResult.Failure -> cleared
        is AppResult.Success -> appLock.clearLockList()
    }
}
