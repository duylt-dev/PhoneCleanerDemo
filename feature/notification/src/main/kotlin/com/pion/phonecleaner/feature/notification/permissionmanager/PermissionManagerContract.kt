package com.pion.phonecleaner.feature.notification.permissionmanager

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.model.permission.PermissionGroup
import com.pion.phonecleaner.domain.model.permission.PermissionGroupId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `permissionmanager` — **one route, three tabs, one State, one ViewModel**
 * (`docs/screens/17-notification-and-permissions.md` §4).
 *
 * It replaces `CantoemblActivity` + `jd.g` + `jd.t` + `jd.y` + `jd.l` + `Tilhassl` + four adapters. The
 * competitor already has one ViewModel shared by the host and every fragment; the fragments exist only
 * because `ViewPager2` needed them. `LLM.md` §3.10 is explicit: **do not create packages for them.**
 *
 * Thirteen other chapters cross-reference this screen, which is why the tab is a route argument: the
 * home screen's permission-centre entry lands on a specific tab.
 */
enum class PermissionTab { Apps, Sensitive, SpecialAccess }

/** The three buckets of the detail sheet. `Other` is the one the competitor discards (§4.5). */
enum class PermissionSection { Sensitive, Normal, Other }

/** `ae.h1`'s six static rows — plus the one thing they never carried: whether the grant is held. */
@Immutable
data class SpecialAccessRow(val access: AppPermission, val isGranted: Boolean)

/**
 * The detail overlay's own state. It holds an **id**, never the object: `Selerover` is `Parcelable` and
 * its `CREATOR` restores only the name, so a `Bundle` round trip silently returns an object with no
 * package, no icon and two empty lists (§4.5). Nothing here is parcelled and the report is looked up
 * from [PermissionManagerState.apps].
 */
@Immutable
data class DetailSheet(
    val packageName: String,
    val expandedSections: ImmutableSet<PermissionSection> =
        persistentSetOf(PermissionSection.Sensitive),
)

data class PermissionManagerState(
    val isScanning: Boolean = true,
    val apps: ImmutableList<AppPermissionReport> = persistentListOf(),
    val groups: ImmutableList<PermissionGroup> = persistentListOf(),
    val specialAccess: ImmutableList<SpecialAccessRow> = persistentListOf(),
    val selectedTab: PermissionTab = PermissionTab.Apps,
    /** Tab 1 expand/collapse. In state, so it survives rotation AND a data refresh. */
    val expandedGroups: ImmutableSet<PermissionGroupId> = persistentSetOf(),
    /** Null = hidden. Replaces `jd.l` + `hasAddedDetailFragment` + `e0(show)`. */
    val detailSheet: DetailSheet? = null,
    /** Set when the user is handed to an app's system settings page; cleared on the next resume. */
    val awaitingRefreshFor: String? = null,
    val error: AppError? = null,
) : UiState {
    val sensitiveAppCount: Int get() = apps.count { it.sensitive.isNotEmpty() }
    val isAppsEmpty: Boolean get() = !isScanning && apps.isEmpty()

    /**
     * Empty groups are filtered **here**, not in `PermissionGrouping`: which groups exist is a property
     * of the catalogue, not of this device's app list. The competitor renders an empty group as a row
     * that looks tappable and is not (§4.5).
     */
    val visibleGroups: ImmutableList<PermissionGroup>
        get() = groups.filterTo(persistentListOf<PermissionGroup>().builder()) { it.apps.isNotEmpty() }
            .build()

    /** The sheet reads THROUGH [apps], so one refresh updates the sheet and both tabs at once. */
    val detailApp: AppPermissionReport?
        get() = detailSheet?.let { sheet -> apps.firstOrNull { it.packageName == sheet.packageName } }
}

sealed interface PermissionManagerIntent : UiIntent {
    data object ScanAnimationFinished : PermissionManagerIntent

    data class TabSelected(val tab: PermissionTab) : PermissionManagerIntent

    /** Fired by the Apps tab AND by a child row of the Sensitive tab — one intent, one path. */
    data class AppRowTapped(val packageName: String) : PermissionManagerIntent

    data class GroupToggled(val id: PermissionGroupId) : PermissionManagerIntent

    data class DetailSectionToggled(val section: PermissionSection) : PermissionManagerIntent

    data object DetailDismissed : PermissionManagerIntent

    data object ManageTapped : PermissionManagerIntent

    data class SpecialAccessTapped(val access: AppPermission) : PermissionManagerIntent

    /**
     * Reported by the composable on every `ON_RESUME`. There is no grant callback for any of these; a
     * re-read on resume is the only signal that exists (§4.1).
     */
    data object ScreenResumed : PermissionManagerIntent

    data object RetryTapped : PermissionManagerIntent

    data object BackPressed : PermissionManagerIntent
}

sealed interface PermissionManagerEffect : UiEffect {
    data class OpenAppSettings(val packageName: String) : PermissionManagerEffect

    data class OpenSpecialAccessSettings(val access: AppPermission) : PermissionManagerEffect

    data object NavigateBack : PermissionManagerEffect

    data class ShowMessage(val error: AppError) : PermissionManagerEffect

    data object ShowScanInProgressMessage : PermissionManagerEffect

    /** The platform has no settings screen for that constant on this device. Never a silent no-op. */
    data object ShowNoSettingsScreenMessage : PermissionManagerEffect
}
