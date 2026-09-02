package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.applock.PinVerdict
import com.pion.phonecleaner.domain.repository.AppLockPinRepository

/**
 * Compares a candidate PIN against the stored digest.
 *
 * **The same use case serves both PIN surfaces** — the in-app `pin` screen and the `lockscreen`
 * overlay (`docs/screens/16-app-lock.md` §3.2) — which is what makes the persisted lockout in
 * `PinLockout` shared rather than per-screen.
 *
 * It returns a [PinVerdict]; it never returns, logs or formats the PIN.
 *
 * Registered `factoryOf(::VerifyPinUseCase)` in `domainModule`.
 */
class VerifyPinUseCase(
    private val pin: AppLockPinRepository,
) {
    suspend operator fun invoke(candidate: String): AppResult<PinVerdict> = pin.verifyPin(candidate)
}
