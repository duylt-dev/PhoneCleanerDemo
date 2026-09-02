package com.pion.phonecleaner.domain.model.applock

import kotlin.time.Instant

/**
 * How many consecutive PIN failures have happened, and until when input is refused.
 *
 * **Persisted, and shared by both PIN surfaces** (`docs/screens/16-app-lock.md` §2.5): the `pin`
 * screen and the `lockscreen` overlay go through one `AppLockPinRepository`, so failing four times
 * on the overlay and then opening the in-app screen does not hand out a fresh allowance, and killing
 * the process does not reset the counter. The competitor has neither — `SacskipActivity.java:107-115`
 * and `GratinActivity.java:63-72` allow unlimited retries against a 10 000-entry space with no
 * backoff at all.
 *
 * [lockedUntil] is an absolute instant rather than a remaining duration precisely so a process kill
 * cannot shorten it: a duration would have to be counted down by something that stays alive.
 */
data class PinLockout(
    /** Consecutive failures since the last success. Reset to 0 by a correct PIN. */
    val failedAttempts: Int = 0,
    /** When input becomes acceptable again, or null when it is acceptable now. */
    val lockedUntil: Instant? = null,
)
