package com.pion.phonecleaner.domain.model.app

import com.pion.phonecleaner.core.common.error.AppError
import kotlinx.collections.immutable.ImmutableList

/**
 * The two-stage enumeration of `docs/screens/14-file-tools-and-app-manager.md` §5.2.
 *
 * [Enumerated] lands first — labels and APK lengths, one `PackageManager` pass — so the list renders
 * immediately; [Sized] fills the measured sizes in one app at a time. The competitor blocks the
 * entire screen behind `awaitAll()` over a `StorageStatsManager` fan-out **and then** pads the wait
 * to 4 000 ms, so a 180-app device stares at a spinner for the one part of the work that is not
 * needed to draw the first screen.
 */
sealed interface InstalledAppsProgress {

    data class Enumerated(val apps: ImmutableList<ManagedApp>) : InstalledAppsProgress

    data class Sized(val stats: AppStorageStats) : InstalledAppsProgress

    /**
     * The package list could not be read at all. An arm rather than a thrown exception, because an
     * exception never crosses a layer boundary (MVI §5) — and rather than an empty list, which is
     * how `cd.d.e` reports a `SecurityException` and why the competitor's user is told nothing.
     */
    data class Failed(val error: AppError) : InstalledAppsProgress
}
