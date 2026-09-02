package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.applock.PinLockout
import com.pion.phonecleaner.domain.model.applock.PinVerdict
import kotlinx.coroutines.flow.Flow

/**
 * The PIN. **The single most important type in this cluster**, because of what it replaces.
 *
 * > The competitor stores the App Lock PIN as **four plaintext ASCII digits** under the preference
 * > key `app_lock_pwd`: one write at `SacskipActivity.java:90` via `putString().commit()`, and six
 * > read sites, every one a plain `String` comparison
 * > (`docs/reverse-engineering/16-app-lock.md` §3.2). There is no digest, no KDF and no Keystore
 * > anywhere in its own packages — the only `MessageDigest` in the whole APK is the MD5 in
 * > `od.s0`, used for duplicate-photo detection. Four digits is a 10 000-entry space, readable from
 * > any backup, any rooted device and any process that can read the app's data directory.
 *
 * This interface is shaped so that the plaintext cannot come back:
 *
 * * **No method returns a PIN, or anything a PIN can be recovered from.** There is no `pin()`, no
 *   `currentPin()`, no `digest()`. [verifyPin] takes a candidate and answers a [PinVerdict].
 * * [savePin] is the **only** writer.
 * * The comparison, the KDF and the salt handling all live below this line, in
 *   `DataStoreAppLockPinRepository` (`appLockDataModule`), on `dispatchers.default`.
 *
 * The lockout lives here too, and not on either ViewModel, because both PIN surfaces must share one
 * allowance and it must survive a process kill (`docs/screens/16-app-lock.md` §2.5).
 */
interface AppLockPinRepository {

    /**
     * Whether a PIN exists. The App Lock gate reads this to decide `PinMode.Set` versus
     * `PinMode.Verify` — the competitor's `od.o0.j()` branch, without reading the secret to do it.
     */
    fun isPinSet(): Flow<Boolean>

    /** The persisted attempt counter and lockout deadline, shared by `pin` and `lockscreen`. */
    fun observeLockout(): Flow<PinLockout>

    /**
     * Compares [pin] against the stored digest in constant time and records the outcome.
     *
     * A rejection increments the counter and may set a deadline; a success clears both. The
     * repository, not the caller, owns that bookkeeping, so the two surfaces cannot drift.
     */
    suspend fun verifyPin(pin: String): AppResult<PinVerdict>

    /**
     * Derives and stores a new digest with a **fresh** salt, and clears the lockout record.
     *
     * The caller has already confirmed the PIN twice (`docs/screens/16-app-lock.md` §2.2); this
     * method does not second-guess that, and it does not read the previous digest.
     */
    suspend fun savePin(pin: String): AppResult<Unit>

    /**
     * Wipes the digest, the salt and the lockout record.
     *
     * Half of `ClearAppLockUseCase` — the only honest answer to a forgotten PIN once the PIN is
     * irreversibly hashed. The other half is `AppLockRepository.clearLockList`.
     */
    suspend fun clearPin(): AppResult<Unit>
}
