package com.pion.phonecleaner.data.applock

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Derives the stored App Lock digest from a PIN and a salt.
 *
 * ### What it replaces
 *
 * `SacskipActivity.java:90` writes the PIN itself:
 * `d0.l(pd.a.b("app_lock_pwd"), pwdStr)` → `prefs.edit().putString(key, value).commit()`, four
 * plaintext ASCII digits, on the main thread. Five further sites read it back and compare with
 * `String` equality. A grep for `MessageDigest|SHA-256|BCrypt|KeyStore` over the competitor's own
 * packages returns two hits, both MD5 over a `File`, whose only caller is duplicate-photo detection
 * (`docs/reverse-engineering/16-app-lock.md` §3.2).
 *
 * ### The algorithm, and why this one
 *
 * **UNKNOWN — the KDF is not settled by the spec.** `docs/screens/16-app-lock.md` §2.5 says
 * *"PBKDF2-HMAC-SHA256 (or Argon2id)"* and fixes neither the choice nor the cost; nothing in
 * `docs/system-architecture.md` or `LLM.md` narrows it. **PBKDF2-HMAC-SHA256 is chosen here**, and
 * the choice is this file's, not the spec's, for one reason: it is in the platform on API 28, the
 * project's `minSdk` — Argon2id is not, and adding it means a native dependency that
 * `gradle/libs.versions.toml` (not this cluster's file) would have to carry.
 *
 * ### An honest statement of what the cost buys
 *
 * [DEFAULT_ITERATIONS] is **UNKNOWN** too — no source names a number. It is set high enough to be
 * felt by a batch attacker and low enough to stay under a screen transition on a 2018 device, and
 * it is stored beside the digest so it can be raised without invalidating existing PINs.
 *
 * But iteration count is **not** what protects a four-digit PIN: the space is 10 000 entries, so an
 * attacker holding both the digest and the salt wins at any cost this side of unusable. The two
 * things that actually protect it are the Keystore-wrapped salt (`KeystoreSaltVault`) and the
 * persisted lockout (`PinLockoutPolicy`). Saying otherwise would be the security-theatre version of
 * the finding this cluster exists to fix.
 */
internal class Pbkdf2PinHasher(
    private val random: SecureRandom = SecureRandom(),
) {

    /** A fresh per-install salt. Called only by `savePin`, so re-keying re-salts. */
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    /**
     * PBKDF2-HMAC-SHA256 over [pin] and [salt].
     *
     * [pin] arrives as a `String` because that is what the keypad buffer joins to, and a `String`
     * cannot be zeroed. It is never stored, never logged and never leaves this call; the digest is
     * the only thing that reaches DataStore.
     */
    fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            // The one buffer this class owns that *can* be cleared, is.
            spec.clearPassword()
        }
    }

    /**
     * Constant-time comparison. `MessageDigest.isEqual` is documented as time-constant for equal
     * lengths; `ByteArray.contentEquals` short-circuits on the first differing byte and leaks how
     * much of a candidate digest was right.
     */
    fun matches(candidate: ByteArray, stored: ByteArray): Boolean =
        MessageDigest.isEqual(candidate, stored)

    internal companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val KEY_BITS = 256
        const val SALT_BYTES = 16

        /** UNKNOWN — see the class KDoc. Stored per PIN so it can be raised later. */
        const val DEFAULT_ITERATIONS = 120_000
    }
}
