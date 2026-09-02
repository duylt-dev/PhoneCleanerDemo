package com.pion.phonecleaner.domain.model.applock

import kotlin.time.Instant

/**
 * What `AppLockPinRepository.verifyPin` decided.
 *
 * It is a sealed type rather than a `Boolean` because three of the four outcomes need to reach the
 * screen differently, and a boolean would collapse the two that matter: "wrong, try again" and
 * "wrong, and you may not try again yet" produce different UI and different analytics, and
 * [NotSet] is a state the gate must not read as a failed attempt.
 *
 * **No arm carries the PIN, and no method anywhere returns it** (`docs/screens/16-app-lock.md`
 * §2.5). The competitor's equivalent *is* the stored PIN: five of its six `app_lock_pwd` sites are
 * plain `String` comparisons against the plaintext preference.
 */
sealed interface PinVerdict {

    /** Correct. The caller may proceed; the lockout record has been reset.  */
    data object Verified : PinVerdict

    /**
     * Wrong. [lockout] is the record **after** this attempt was counted, so a caller renders the
     * remaining allowance without a second read.
     */
    data class Rejected(val lockout: PinLockout) : PinVerdict

    /** Input was refused without being compared: the lockout has not elapsed. */
    data class LockedOut(val until: Instant) : PinVerdict

    /**
     * No PIN has been set, so there is nothing to compare against.
     *
     * A first-class outcome rather than a failure: it is exactly the branch `od.o0.j()` takes when
     * `app_lock_pwd` is blank, and the caller's answer is to send the user to `PinMode.Set` — not
     * to show an error (`docs/reverse-engineering/16-app-lock.md` §3.2).
     */
    data object NotSet : PinVerdict
}
