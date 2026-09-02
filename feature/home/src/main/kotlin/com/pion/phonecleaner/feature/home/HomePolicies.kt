package com.pion.phonecleaner.feature.home

import kotlin.time.Duration.Companion.seconds

/**
 * The three decisions home makes for itself, as pure functions the reducer calls.
 *
 * They are here rather than inside `HomeViewModel` because each is a rule with a stated reason, and
 * a rule kept in a `when` arm is a rule nobody tests. Everything *business* about the junk estimate
 * — the TTL, the single-flight guard, what counts as junk — stays in `JunkRepository`
 * (MVI §3 rule 7, `docs/screens/12-junk-cleaning.md` §5); the screen owns only how long it will keep
 * a spinner up, how a cached figure reads, and when it may ask for notifications.
 */

/**
 * Three named cases from one nullable Long, resolved **once, here**.
 *
 * The competitor derives the same three at render time from two booleans and a `-1` sentinel
 * (`N1()` :750-770), and the consequence is that "we have not looked yet" and "there is nothing
 * here" draw identically (delta 13). `null` is the repository's own "no cached estimate"; `0` is a
 * measurement that came back empty, and it is not the same statement.
 */
internal fun Long?.toJunkPill(): JunkPill = when {
    this == null -> JunkPill.NotMeasured
    this <= 0L -> JunkPill.Clean
    else -> JunkPill.Measured(this)
}

/**
 * The bounded wait around a refresh, so the busy flag cannot strand a spinner on a screen the user
 * returns to constantly.
 *
 * UNKNOWN — no source states a value. `docs/screens/11-home.md` §1.2 requires the bound
 * ("`withTimeoutOrNull(JUNK_ESTIMATE_TIMEOUT)` around the estimate, so the spinner cannot strand")
 * and names the constant without giving it a number; §4.1's stuck-state case asserts the flag is
 * down *at* the timeout without saying when that is. Looked in that appendix,
 * `docs/screens/12-junk-cleaning.md` §5 and `LLM.md` §12. Ten seconds is chosen as the shortest wait
 * that a cold storage walk on a slow device can plausibly finish inside; it is a screen-level
 * patience setting, so a wrong value costs a re-run on the next resume and nothing else.
 */
internal val JUNK_ESTIMATE_TIMEOUT = 10.seconds

/**
 * Door 1 of the notification opt-in — the competitor's `v0()`, offered from `onResume`.
 *
 * Three conditions, and the third is the one the competitor gets right and its *other* door gets
 * wrong: an arrival **from a notification** must not be answered with a request to turn notifications
 * on. Its banner (door 2) bypasses that check, its counter and its remote-config cap all three
 * (`docs/reverse-engineering/11-home.md` §7.3); here both doors are the same intent.
 *
 * [HomeState.hasOfferedNotificationSheet] is raised when the sheet is *shown*, not when it is
 * answered, because it is what stops the sheet reappearing on every resume of one session. That is
 * not the latch `LLM.md` §7.4 forbids writing early: this field is session state and is **not**
 * persisted, so a process death restores the offer rather than burning it — which is exactly the
 * failure that rule names.
 */
internal fun HomeState.shouldOfferNotificationSheet(): Boolean =
    showNotificationBanner && !hasOfferedNotificationSheet && !arrivedFromNotification
