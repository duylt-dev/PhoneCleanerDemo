package com.pion.phonecleaner.data.applock

import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.data.datastore.AppLockPrefs
import com.pion.phonecleaner.domain.model.applock.PIN_LENGTH
import com.pion.phonecleaner.domain.model.applock.PinLockout
import com.pion.phonecleaner.domain.model.applock.PinVerdict
import com.pion.phonecleaner.domain.policy.PinLockoutPolicy
import com.pion.phonecleaner.domain.repository.AppLockPinRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Instant

/**
 * The App Lock PIN, stored as a salted digest — the replacement for `app_lock_pwd`.
 *
 * > **What is being replaced.** The competitor keeps the PIN as four plaintext ASCII digits under
 * > `app_lock_pwd`: one write at `SacskipActivity.java:90` via `putString().commit()`, and six read
 * > sites, every one a plain `String` comparison. There is no digest, no KDF and no Keystore
 * > anywhere in its own packages — the only `MessageDigest` in the whole APK is the MD5 in `od.s0`,
 * > for duplicate-photo detection (`docs/reverse-engineering/16-app-lock.md` §3.2).
 *
 * Here: PBKDF2-HMAC-SHA256 over a per-install random salt, the salt wrapped by a non-exportable
 * Android Keystore key, a constant-time comparison, and a persisted lockout shared by both PIN
 * surfaces. See `Pbkdf2PinHasher` and `KeystoreSaltVault` for the choices and what each one buys.
 *
 * The KDF runs on `dispatchers.default` **inside this class**; no caller names a dispatcher
 * (`LLM.md` §6.5). DECLARED IN `appLockDataModule`.
 */
internal class DataStoreAppLockPinRepository(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
    private val clock: AppClock,
    private val log: AppLogger,
    private val hasher: Pbkdf2PinHasher = Pbkdf2PinHasher(),
    private val vault: KeystoreSaltVault = KeystoreSaltVault(),
) : AppLockPinRepository {

    override fun isPinSet(): Flow<Boolean> =
        dataStore.data.map { it[AppLockPrefs.PIN_DIGEST].isNullOrEmpty().not() }
            .distinctUntilChanged()

    override fun observeLockout(): Flow<PinLockout> =
        dataStore.data.map { it.readLockout() }.distinctUntilChanged()

    override suspend fun verifyPin(pin: String): AppResult<PinVerdict> =
        withContext(dispatchers.default) {
            runCatching {
                val snapshot = readSnapshot() ?: return@runCatching PinVerdict.NotSet
                val now = clock.now()
                if (PinLockoutPolicy.isLockedOut(snapshot.lockout, now)) {
                    // Refused WITHOUT comparing: a locked-out attempt must not be a free oracle,
                    // and it must not count as a further failure either.
                    return@runCatching PinVerdict.LockedOut(requireNotNull(snapshot.lockout.lockedUntil))
                }
                val candidate = hasher.derive(pin, snapshot.salt, snapshot.iterations)
                if (hasher.matches(candidate, snapshot.digest)) {
                    writeLockout(PinLockoutPolicy.onSuccess())
                    PinVerdict.Verified
                } else {
                    val next = PinLockoutPolicy.onFailure(snapshot.lockout, now)
                    writeLockout(next)
                    PinVerdict.Rejected(next)
                }
            }.toAppResult("verifyPin")
        }

    override suspend fun savePin(pin: String): AppResult<Unit> = withContext(dispatchers.default) {
        if (pin.length != PIN_LENGTH) {
            // The repository refuses a length the domain does not define, rather than storing a
            // digest that no keypad can ever reproduce.
            return@withContext AppResult.Failure(AppError.Unexpected("PIN must be $PIN_LENGTH digits"))
        }
        runCatching {
            val salt = hasher.newSalt()
            val iterations = Pbkdf2PinHasher.DEFAULT_ITERATIONS
            val digest = hasher.derive(pin, salt, iterations)
            val wrappedSalt = vault.wrap(salt)
            dataStore.edit { prefs ->
                prefs[AppLockPrefs.PIN_DIGEST] = digest.encode()
                prefs[AppLockPrefs.PIN_SALT] = wrappedSalt
                prefs[AppLockPrefs.PIN_ITERATIONS] = iterations
                prefs.clearLockout()
            }
            Unit
        }.toAppResult("savePin")
    }

    override suspend fun clearPin(): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            dataStore.edit { prefs ->
                prefs.remove(AppLockPrefs.PIN_DIGEST)
                prefs.remove(AppLockPrefs.PIN_SALT)
                prefs.remove(AppLockPrefs.PIN_ITERATIONS)
                prefs.clearLockout()
            }
            Unit
        }.toAppResult("clearPin")
    }

    /**
     * Everything one verification needs, read in one pass.
     *
     * `null` means no usable PIN — either none was ever set, or the salt cannot be unwrapped because
     * the Keystore key is gone (a restore onto another device, a cleared Keystore). Both report as
     * `NotSet`, which sends the user to `PinMode.Set` rather than to an error they cannot act on.
     */
    private suspend fun readSnapshot(): PinSnapshot? {
        val prefs = dataStore.data.first()
        val digest = prefs[AppLockPrefs.PIN_DIGEST]?.takeIf { it.isNotEmpty() } ?: return null
        val wrappedSalt = prefs[AppLockPrefs.PIN_SALT] ?: return null
        val salt = runCatching { vault.unwrap(wrappedSalt) }
            .onFailure { log.e(it) { "App Lock salt could not be unwrapped; treating the PIN as unset" } }
            .getOrNull() ?: return null
        return PinSnapshot(
            digest = digest.decode(),
            salt = salt,
            iterations = prefs[AppLockPrefs.PIN_ITERATIONS] ?: Pbkdf2PinHasher.DEFAULT_ITERATIONS,
            lockout = prefs.readLockout(),
        )
    }

    private suspend fun writeLockout(lockout: PinLockout) {
        dataStore.edit { prefs ->
            prefs[AppLockPrefs.FAILED_ATTEMPTS] = lockout.failedAttempts
            val until = lockout.lockedUntil
            if (until == null) prefs.remove(AppLockPrefs.LOCKED_UNTIL)
            else prefs[AppLockPrefs.LOCKED_UNTIL] = until.toEpochMilliseconds()
        }
    }

    /**
     * One conversion for every path out of this class. A thrown `GeneralSecurityException` — a gone
     * Keystore key, an unavailable KDF — becomes `AppError.Unexpected` and is logged; it never
     * crosses the repository boundary as an exception (`LLM.md` §4).
     */
    private fun <T> Result<T>.toAppResult(what: String): AppResult<T> = fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = {
            log.e(it) { "App Lock $what failed" }
            AppResult.Failure(AppError.Unexpected(it.message))
        },
    )

    /** Not a `data class`: two `ByteArray` fields would give it an `equals` that compares refs. */
    private class PinSnapshot(
        val digest: ByteArray,
        val salt: ByteArray,
        val iterations: Int,
        val lockout: PinLockout,
    )
}

private fun Preferences.readLockout(): PinLockout = PinLockout(
    failedAttempts = this[AppLockPrefs.FAILED_ATTEMPTS] ?: 0,
    lockedUntil = this[AppLockPrefs.LOCKED_UNTIL]
        ?.takeIf { it > 0L }
        ?.let { Instant.fromEpochMilliseconds(it) },
)

private fun androidx.datastore.preferences.core.MutablePreferences.clearLockout() {
    remove(AppLockPrefs.FAILED_ATTEMPTS)
    remove(AppLockPrefs.LOCKED_UNTIL)
}

private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)
