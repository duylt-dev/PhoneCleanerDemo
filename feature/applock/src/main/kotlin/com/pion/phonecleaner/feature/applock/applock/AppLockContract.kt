package com.pion.phonecleaner.feature.applock.applock

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.applock.LockableApp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** Which of the two pages is showing. Replaces the Activity's `vp_adapter` field. */
enum class AppLockTab { All, Locked }

/**
 * The App Lock home (`docs/screens/16-app-lock.md` §1.1), replacing `SemantanActivity` + `jd.x` +
 * `jd.c` + `Chaennia`.
 *
 * Eight competitor holders collapse into this one object: three `MutableLiveData` on `Chaennia`
 * (two with public setters), the adapter, the tab index, and three Activity fields for dialogs. None
 * of them survives rotation, so a rotate restarts the four-second scan
 * (`docs/screens/16-app-lock.md` §1.5).
 *
 * `apps` is `ImmutableList` and `togglingPackages` is `ImmutableSet`, not `List`/`Set`: listing
 * `kotlin.collections.List` in `compose-stability.conf` tells the compiler to *treat* it as stable,
 * it does not make it so, and the competitor's equivalent is a `java.util.List` field that
 * `kd.e.c()` clears and refills in place (`LLM.md` §8).
 */
data class AppLockState(
    /**
     * The scan is a **presentation stage with its own exit condition**, not `isLoading`. The list
     * exists before it ends; what the stage does is delay showing it.
     */
    val isScanning: Boolean = true,
    val apps: ImmutableList<LockableApp> = persistentListOf(),
    val selectedTab: AppLockTab = AppLockTab.All,
    val hasOverlayPermission: Boolean = false,
    val hasUsageStatsPermission: Boolean = false,
    val lockNewlyInstalled: Boolean = true,
    /** Which app the unlock sheet is asking about. An id, never the object. */
    val pendingUnlockPackage: String? = null,
    val isPermissionSheetVisible: Boolean = false,
    /**
     * Rows with a write in flight, so a second tap is dropped without freezing the list.
     *
     * The competitor de-duplicates row taps with a **process-global** 500 ms click debounce
     * (`java/md/g1.java:61, 426-432`), so a tap anywhere blocks a tap everywhere.
     */
    val togglingPackages: ImmutableSet<String> = persistentSetOf(),
    val error: AppError? = null,
) : UiState {

    val lockedApps: ImmutableList<LockableApp> get() = apps.filter { it.isLocked }.toImmutableList()

    /**
     * Over a list the repository has already reconciled against `PackageManager`, so this and the
     * store cannot disagree — `od.o0.h()` counts installed packages over an unpruned store.
     */
    val lockedCount: Int get() = lockedApps.size

    /** Both special accesses. `FeatureCatalog.requires(FeatureId.AppLock)` is these two. */
    val hasAllPermissions: Boolean get() = hasOverlayPermission && hasUsageStatsPermission

    val visibleApps: ImmutableList<LockableApp>
        get() = if (selectedTab == AppLockTab.All) apps else lockedApps

    val isEmpty: Boolean get() = !isScanning && visibleApps.isEmpty()

    /** The sheet renders from the live list, so an unlock cannot ask about a stale row. */
    val pendingUnlockApp: LockableApp?
        get() = pendingUnlockPackage?.let { pkg -> apps.firstOrNull { it.packageName == pkg } }
}

sealed interface AppLockIntent : UiIntent {
    /**
     * The scan stage is over.
     *
     * The competitor shows its list only when a Lottie `onAnimationEnd` fires: clear the animation,
     * detach the view or disable animations system-wide and the tabs never appear
     * (`docs/screens/16-app-lock.md` §1.5). Here the animation is decoration, and a
     * `LaunchedEffect` timeout raises this intent regardless.
     */
    data object ScanAnimationFinished : AppLockIntent

    data class TabSelected(val tab: AppLockTab) : AppLockIntent

    /** The event, not the mutation: the reducer decides what a tap on this row means. */
    data class AppRowTapped(val packageName: String) : AppLockIntent

    data object UnlockConfirmed : AppLockIntent
    data object UnlockDismissed : AppLockIntent
    data object PermissionSheetDismissed : AppLockIntent
    data object GrantOverlayTapped : AppLockIntent
    data object GrantUsageStatsTapped : AppLockIntent
    data class LockNewlyInstalledChanged(val enabled: Boolean) : AppLockIntent

    /**
     * Reported by the composable on every `ON_START`.
     *
     * There is no grant callback for either special access, so re-reading on start is the only
     * signal that exists — and it is what the competitor's Permission Centre lacks, leaving a stale
     * card on screen after the user grants and presses back (`LLM.md` §7.4).
     */
    data class PermissionsResolved(val overlay: Boolean, val usageStats: Boolean) : AppLockIntent

    data object SettingsTapped : AppLockIntent
    data object BackPressed : AppLockIntent
}

sealed interface AppLockEffect : UiEffect {
    data object NavigateToSettings : AppLockEffect
    data object NavigateBack : AppLockEffect

    /** The ViewModel never builds an `Intent`; the Route does (MVI §4). */
    data object RequestOverlayPermission : AppLockEffect
    data object RequestUsageStatsPermission : AppLockEffect

    /** Carries the error: reading `state.error` in the collector reads the pre-failure value. */
    data class ShowMessage(val error: AppError) : AppLockEffect
}
