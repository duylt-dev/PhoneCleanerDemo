package com.pion.phonecleaner.navigation

import android.content.Intent
import com.pion.phonecleaner.domain.model.launch.LaunchSource

/** The extra key the app's own notifications and widget set. */
private const val EXTRA_LAUNCH_SOURCE = "com.pion.phonecleaner.extra.LAUNCH_SOURCE"

/**
 * The mapping, and nothing else, reads an Intent (LLM.md §3.9).
 *
 * Unrecognised values fall back to [LaunchSource.Launcher] rather than throwing: an Intent is
 * attacker-reachable input, and a crash on a malformed extra is a denial of service on the launcher icon.
 */
fun Intent?.launchSource(): LaunchSource {
    val raw = this?.getStringExtra(EXTRA_LAUNCH_SOURCE) ?: return LaunchSource.Launcher
    return LaunchSource.entries.firstOrNull { it.name == raw } ?: LaunchSource.Launcher
}
