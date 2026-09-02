package com.pion.phonecleaner.feature.files.appmanager

import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.app.AppStorageStats
import com.pion.phonecleaner.domain.model.app.ManagedApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The pure half of `appmanager`: state in, state out, no coroutine and no repository.
 *
 * Six competitor comparators and a reset-then-flip that lands descending **by accident** collapse
 * into [sortedWith] and [withSortSelected] (§5.2).
 */
internal fun AppManagerState.withEnumerated(
    apps: ImmutableList<ManagedApp>,
    restoredSelection: ImmutableSet<String>,
): AppManagerState {
    val present = apps.mapTo(mutableSetOf(), ManagedApp::packageName)
    val keep = (restoredSelection + selectedPackages).filterTo(mutableSetOf(), present::contains)
    return copy(
        apps = apps.sortedWith(sort),
        selectedPackages = keep.toImmutableSet(),
        sizedCount = apps.count(ManagedApp::sizeKnown),
    )
}

/**
 * One measured app. The row is replaced in place and the list is **not** re-sorted mid-stream: a row
 * that jumps under the user's finger while sizes stream in is worse than a briefly stale order. The
 * order settles when the scan finishes.
 */
internal fun AppManagerState.withSized(stats: AppStorageStats): AppManagerState {
    var changed = false
    val next = apps.map { app ->
        if (app.packageName != stats.packageName) {
            app
        } else {
            changed = true
            app.copy(stats = stats)
        }
    }
    if (!changed) return this
    return copy(apps = next.toImmutableList(), sizedCount = next.count(ManagedApp::sizeKnown))
}

/** Sizes have stopped arriving: sort once, on final values. */
internal fun AppManagerState.withScanFinished(): AppManagerState =
    copy(phase = ToolPhase.Completing, apps = apps.sortedWith(sort))

/** A re-tap flips the direction; a different key lands descending, and it is stated, not accidental. */
internal fun AppManagerState.withSortSelected(key: AppSortKey): AppManagerState {
    val next = if (sort.key == key) sort.copy(descending = !sort.descending) else AppSort(key, true)
    return copy(sort = next, apps = apps.sortedWith(next))
}

internal fun AppManagerState.withToggled(packageName: String): AppManagerState = copy(
    selectedPackages = if (packageName in selectedPackages) {
        (selectedPackages - packageName).toImmutableSet()
    } else {
        (selectedPackages + packageName).toImmutableSet()
    },
)

/**
 * An unmeasured value sorts **last in both directions**, never among the real ones: an app whose
 * `StorageStatsManager` query was refused has no size, and the competitor's `1000L` default sorts it
 * among the tiny apps and renders as *"1000 B"* (§5.5). The same holds for a `0` last-used stamp,
 * which means "not seen inside the usage window", not "used in 1970".
 */
internal fun AppManagerState.withSelectAllToggled(): AppManagerState = copy(
    selectedPackages = if (selectedPackages.size == apps.size) {
        persistentSetOf()
    } else {
        apps.mapTo(mutableSetOf(), ManagedApp::packageName).toImmutableSet()
    },
)

internal fun ImmutableList<ManagedApp>.sortedWith(sort: AppSort): ImmutableList<ManagedApp> {
    val known: (ManagedApp) -> Boolean = when (sort.key) {
        AppSortKey.Size -> ManagedApp::sizeKnown
        AppSortKey.InstallDate -> { app -> app.firstInstallEpochMillis > 0L }
        AppSortKey.LastUsed -> { app -> app.lastUsedEpochMillis > 0L }
    }
    val value: (ManagedApp) -> Long = when (sort.key) {
        AppSortKey.Size -> ManagedApp::totalBytes
        AppSortKey.InstallDate -> ManagedApp::firstInstallEpochMillis
        AppSortKey.LastUsed -> ManagedApp::lastUsedEpochMillis
    }
    val (measured, unmeasured) = partition(known)
    val ordered = measured.sortedBy(value).let { if (sort.descending) it.asReversed() else it }
    return (ordered + unmeasured).toImmutableList()
}
