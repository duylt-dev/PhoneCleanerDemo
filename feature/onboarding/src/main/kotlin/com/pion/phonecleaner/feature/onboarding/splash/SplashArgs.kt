package com.pion.phonecleaner.feature.onboarding.splash

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.model.launch.LaunchSource

/**
 * Reads the route argument without asserting how Navigation stored it: a type-safe route may hand
 * back the enum itself or its `name`, and `SavedStateHandle.get<T>` is an unchecked cast that would
 * fail at the use site rather than here. Unrecognised input falls back to
 * [LaunchSource.Launcher] — an Intent is attacker-reachable, and a crash on a malformed extra is a
 * denial of service on the launcher icon (`LLM.md` §7.3).
 *
 * The key is the route data class's own property name under type-safe navigation, so the reported
 * route declaration names its property `source` and this constant matches it.
 */
internal fun SavedStateHandle.launchSource(): LaunchSource = when (val raw = get<Any?>(ARG_SOURCE)) {
    is LaunchSource -> raw
    is String -> LaunchSource.entries.firstOrNull { it.name == raw } ?: LaunchSource.Launcher
    else -> LaunchSource.Launcher
}

private const val ARG_SOURCE = "source"
