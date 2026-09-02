package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.applock.PinLockout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * How long input is refused after N consecutive wrong PINs.
 *
 * A **pure function over two values**, with no I/O and no platform — a unit test with no fakes
 * (`LLM.md` §4). It lives here and not inside `AppLockPinRepository`'s implementation because both
 * PIN surfaces depend on the answer being the same one, and because a curve nobody can read off the
 * code is how the competitor ended up with no curve at all: `SacskipActivity.java:107-115` and
 * `GratinActivity.java:63-72` allow unlimited retries against a 10 000-entry space, with no counter,
 * no backoff and no lockout of any kind.
 *
 * ### The numbers are UNKNOWN, and deliberately in one place
 *
 * `docs/screens/16-app-lock.md` §2.5 states the shape — *"exponential lockout after 5 consecutive
 * failures, persisted"* — and §6 item 5 records that the curve itself *"is not settled by any
 * report"*: how many failures, how long, and whether biometrics share the allowance. So
 * [THRESHOLD] is the spec's; [BASE] and [CEILING] are **this file's choices**, and they are named
 * constants in one object precisely so an owner can change the policy by editing three lines rather
 * than hunting two ViewModels.
 *
 * Chosen to be recoverable rather than punitive: five free attempts covers a mistyped PIN, and the
 * curve reaches the ceiling in four further failures, by which point an attacker is spending half an
 * hour per guess against a space that needs thousands.
 */
object PinLockoutPolicy {

    /** Consecutive failures allowed before any lockout. The spec's number. */
    const val THRESHOLD: Int = 5

    /** The first lockout, doubling per failure after that. UNKNOWN — see the KDoc. */
    val BASE: Duration = 30.seconds

    /** The longest a lockout ever gets. UNKNOWN — see the KDoc. */
    val CEILING: Duration = 30.minutes

    /**
     * The record after one more failure at [now].
     *
     * The deadline is an absolute [Instant], not a remaining duration, so a process kill cannot
     * shorten it — a duration would have to be counted down by something that stays alive, which
     * after a swipe-kill is nothing.
     */
    fun onFailure(previous: PinLockout, now: Instant): PinLockout {
        val attempts = previous.failedAttempts + 1
        val penalty = penaltyFor(attempts)
        return PinLockout(
            failedAttempts = attempts,
            lockedUntil = penalty?.let { now + it },
        )
    }

    /** Cleared. A correct PIN forgives every previous failure — the counter is *consecutive*. */
    fun onSuccess(): PinLockout = PinLockout()

    /** Whether [lockout] still refuses input at [now]. An elapsed deadline is not a lockout. */
    fun isLockedOut(lockout: PinLockout, now: Instant): Boolean {
        val until = lockout.lockedUntil ?: return false
        return now < until
    }

    /** `null` below [THRESHOLD]: the first four wrong entries cost nothing but the attempt. */
    private fun penaltyFor(attempts: Int): Duration? {
        if (attempts < THRESHOLD) return null
        val doublings = (attempts - THRESHOLD).coerceAtMost(MAX_DOUBLINGS)
        val scaled = BASE * (1L shl doublings).toDouble()
        return if (scaled > CEILING) CEILING else scaled
    }

    /** Enough doublings to pass [CEILING] from [BASE]; more would only overflow the shift. */
    private const val MAX_DOUBLINGS = 16
}
