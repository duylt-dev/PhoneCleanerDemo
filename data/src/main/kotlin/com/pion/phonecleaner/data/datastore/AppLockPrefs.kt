package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * The key table for App Lock (`LLM.md` §3.6, §4). A key table only: the reading, the writing and
 * every rule live in the cluster's repositories in `data/applock/`.
 *
 * It replaces four `od.d0` keys — `app_lock_list`, `app_lock_enable`, `app_lock_new_app` and
 * `app_lock_pwd` — all four written with `commit()` on the calling thread
 * (`java/od/d0.java:78-81`).
 *
 * **None of these names is the competitor's.** `LegacyPrefsMigrationWorker` reads those once and
 * never again (`AppDataStore` KDoc), so sharing a name would make the migration read its own output.
 *
 * ### `app_lock_pwd` has no counterpart here, and that is the point
 *
 * The competitor stores four plaintext ASCII digits under that key — one write
 * (`SacskipActivity.java:90`), six reads, every one a `String` comparison, and no digest, KDF or
 * Keystore anywhere in its own packages (`docs/reverse-engineering/16-app-lock.md` §3.2). What is
 * stored here instead is [PIN_DIGEST] (a PBKDF2 output), [PIN_SALT] (random, and wrapped by an
 * Android Keystore key so a file-level copy of this store is not enough to attack it) and
 * [PIN_ITERATIONS]. Nothing in this file can be turned back into a PIN.
 */
internal object AppLockPrefs {

    /** Packages behind the lock. A `Set<String>`, so membership is a set test — see below. */
    val LOCKED_PACKAGES = stringSetPreferencesKey("app_lock_locked_packages")

    /** The master switch. Defaults to `true`, for parity with `app_lock_enable`. */
    val ENABLED = booleanPreferencesKey("app_lock_enabled")

    /** Offer to lock a newly installed app. Defaults to `true`, parity with `app_lock_new_app`. */
    val LOCK_NEW_APPS = booleanPreferencesKey("app_lock_lock_new_apps")

    /** Base64 of the PBKDF2 output. Never the PIN, and nothing a PIN is recoverable from. */
    val PIN_DIGEST = stringPreferencesKey("app_lock_pin_digest")

    /**
     * Base64 of the **Keystore-wrapped** per-install salt: `iv || AES-GCM(salt)`.
     *
     * Wrapped rather than stored raw so that copying this preferences file off the device — from a
     * backup, or with root — yields a salt that cannot be unwrapped without the hardware-backed key,
     * which is what makes a 10 000-entry PIN space survivable at all.
     */
    val PIN_SALT = stringPreferencesKey("app_lock_pin_salt")

    /**
     * The iteration count the stored digest was derived with.
     *
     * Recorded per-PIN so the cost can be raised later without invalidating every existing PIN: a
     * verify derives with the count that was used, and `savePin` writes today's.
     */
    val PIN_ITERATIONS = intPreferencesKey("app_lock_pin_iterations")

    /** Consecutive PIN failures since the last success. Shared by both PIN surfaces. */
    val FAILED_ATTEMPTS = intPreferencesKey("app_lock_failed_attempts")

    /** Epoch millis until which input is refused; absent or `0` means "not locked out". */
    val LOCKED_UNTIL = longPreferencesKey("app_lock_locked_until")
}
