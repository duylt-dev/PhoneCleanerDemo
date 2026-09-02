package com.pion.phonecleaner.feature.antivirus.result

import androidx.lifecycle.SavedStateHandle

/**
 * Reads the one route argument without asserting how Navigation stored it: a type-safe route may
 * hand back an `Int` or its `String` form, and `SavedStateHandle.get<T>` is an unchecked cast that
 * would fail at the use site rather than here. Anything unreadable is `0` — the headline then shows
 * nothing until the first emission, which is a second later and is the truth.
 *
 * **The argument carries a count, never the list.** That is the central fix of this screen: the
 * competitor passes the findings as a Gson blob in an `Intent` extra and reads them back with
 * `checkNotNull`, an NPE on the main thread at `onCreate` for any malformed extra, reachable from
 * any external `startActivity` (§2.5).
 *
 * The key is the route data class's own property name under type-safe navigation, so the reported
 * route declaration names its property `findingCount` and this constant matches it.
 */
internal fun SavedStateHandle.findingCount(): Int = when (val raw = get<Any?>(ARG_FINDING_COUNT)) {
    is Int -> raw.coerceAtLeast(0)
    is String -> raw.toIntOrNull()?.coerceAtLeast(0) ?: 0
    else -> 0
}

private const val ARG_FINDING_COUNT = "findingCount"
