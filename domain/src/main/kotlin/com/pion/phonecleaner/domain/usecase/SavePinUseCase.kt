package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.AppLockPinRepository

/**
 * Stores a new PIN (`docs/screens/16-app-lock.md` §2.2).
 *
 * The caller has already had the PIN entered twice and compared the two entries; in `PinMode.Change`
 * it has also verified the old PIN first, which the competitor never does
 * (`SacskipActivity.java:99-105`).
 *
 * What happens below this line is a fresh salt and a KDF — see `AppLockPinRepository`'s KDoc for the
 * plaintext preference it replaces.
 *
 * Registered `factoryOf(::SavePinUseCase)` in `domainModule`.
 */
class SavePinUseCase(
    private val pin: AppLockPinRepository,
) {
    suspend operator fun invoke(newPin: String): AppResult<Unit> = pin.savePin(newPin)
}
