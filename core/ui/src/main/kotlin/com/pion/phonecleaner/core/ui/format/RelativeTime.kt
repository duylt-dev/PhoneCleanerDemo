package com.pion.phonecleaner.core.ui.format

import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * "5 minutes ago", in the platform's own translation of it.
 *
 * `DateUtils` is used rather than a hand-rolled table because the strings are already localised in
 * every locale the device has, including the plural rules — writing them here would mean shipping and
 * translating 17 copies of a sentence Android already owns. It replaces `md.h4`, a **static GMT**
 * `SimpleDateFormat` plus an `isToday` (system-architecture:837), which reports the wrong day for
 * every user not on GMT.
 *
 * It is a plain function, not a composable, because its call site is inside a `remember` block:
 * `remember(n.postedAt) { relativeTimestamp(n.postedAt) }` (`docs/screens/17` §2.4).
 *
 * @param now injectable so a test can pin it. It defaults to the wall clock rather than reaching for
 *   the injected `AppClock`, because this runs inside a composition where nothing is injected; a
 *   ViewModel that needs the same sentence has the clock and does not call this.
 */
fun relativeTimestamp(
    epochMillis: Long,
    now: Long = System.currentTimeMillis(),
): String = DateUtils.getRelativeTimeSpanString(
    epochMillis,
    now,
    DateUtils.MINUTE_IN_MILLIS,
).toString()

/**
 * The composable form, for a row that does not already have a `remember` of its own.
 *
 * It does **not** tick. A list of a thousand notifications re-reading the clock once a second would
 * repaint every visible row once a second (MVI §8); a screen that genuinely wants a live relative
 * time drives it from its own state.
 */
@Composable
fun rememberRelativeTimestamp(epochMillis: Long): String =
    remember(epochMillis) { relativeTimestamp(epochMillis) }
