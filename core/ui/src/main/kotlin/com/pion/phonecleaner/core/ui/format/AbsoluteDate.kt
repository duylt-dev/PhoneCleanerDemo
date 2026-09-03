package com.pion.phonecleaner.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * A calendar date — "10 Jul 2026" — in the composition's locale and the **device's** time zone.
 *
 * It exists beside [relativeTimestamp], which it does not replace. The two answer different
 * questions: *how long ago* ("5 minutes ago") reads better for something that just happened, and a
 * date reads better for a field the user compares across rows. An install date is the second kind —
 * `getRelativeTimeSpanString` would render a two-year-old install as "Jul 10, 2026" anyway and a
 * two-day-old one as "2 days ago", so a list of them could not be scanned as one column.
 *
 * The zone is [ZoneId.systemDefault], read at format time. `md.h4` — the routine this replaces — is a
 * **static GMT** `SimpleDateFormat` (`docs/system-architecture.md:837`), which names the wrong day
 * for every user not on GMT, for the whole span of the day where the offset crosses midnight.
 *
 * `0` is not a date and this never renders one. A caller holding "unknown" as `0` must branch before
 * it gets here — the competitor does not, and formats a byte count through a `yyyy-MM-dd` formatter
 * to produce a 1970 "installation time" (`docs/screens/14` §5.5).
 */
fun absoluteDate(
    epochMillis: Long,
    locale: Locale,
    zone: ZoneId = ZoneId.systemDefault(),
): String = DateTimeFormatter
    .ofLocalizedDate(FormatStyle.MEDIUM)
    .withLocale(locale)
    .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

/**
 * The composable form. Keyed on the locale as well as the value, so the in-app language picker
 * re-formats every visible date without a process restart — the same rule `rememberByteFormat`
 * follows, and for the same reason.
 */
@Composable
fun rememberAbsoluteDate(epochMillis: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(epochMillis, locale) { absoluteDate(epochMillis, locale) }
}
