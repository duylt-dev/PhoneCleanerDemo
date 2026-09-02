package com.pion.phonecleaner.core.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.pion.phonecleaner.core.common.format.ByteFormatter
import com.pion.phonecleaner.core.common.format.FormattedSize
import java.util.Locale

/**
 * `ByteFormatter` bound to the composition's locale.
 *
 * `ByteFormatter` itself is a stateless `object` in `:core:common` and is deliberately **not in
 * Koin**: it has to work inside a `RemoteViews` build and a `CoroutineWorker` as well as a
 * composition, and injecting it would deny it to two of its three call sites (LLM.md §12). This is
 * the third call site's door, and the only thing it adds is the locale.
 *
 * **A ViewModel never calls it** (`docs/screens/21` §6.1) — a ViewModel must not build user-facing
 * copy. It hands out `Long` bytes; the screen decides how those read.
 *
 * The competitor has five disagreeing formatters, one of which parses formatted bytes back into a
 * `Long` and defaults an unknown unit to MB (`md.g4.e`, `docs/screens/11-home.md` §1.4).
 */
@Immutable
class ByteFormat internal constructor(private val locale: Locale) {
    fun size(bytes: Long): FormattedSize = ByteFormatter.size(bytes, locale)

    fun rate(bytesPerSecond: Long): FormattedSize = ByteFormatter.rate(bytesPerSecond, locale)
}

/**
 * Keyed on the configuration's locale, so an in-app language change re-formats every visible size —
 * the app ships 17 locales and the picker changes them without a process restart.
 */
@Composable
fun rememberByteFormat(): ByteFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) { ByteFormat(locale) }
}

/** The one-shot form: `rememberByteFormat(state.selectedBytes)` (`docs/screens/12` §3). */
@Composable
fun rememberByteFormat(bytes: Long): String = rememberByteFormat().size(bytes).toString()
