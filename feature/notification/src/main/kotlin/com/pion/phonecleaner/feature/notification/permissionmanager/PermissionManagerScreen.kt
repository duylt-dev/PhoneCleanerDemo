package com.pion.phonecleaner.feature.notification.permissionmanager

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppBottomSheet
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.component.state.LoadingOverlay
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.permissionmanager.component.AppPermissionDetail
import com.pion.phonecleaner.feature.notification.permissionmanager.component.AppsTab
import com.pion.phonecleaner.feature.notification.permissionmanager.component.SensitiveTab
import com.pion.phonecleaner.feature.notification.permissionmanager.component.SpecialAccessTab

/**
 * `docs/screens/17-notification-and-permissions.md` §4.3.
 *
 * Three tabs reading three slices of one `State` — the same object graph as the competitor's, with the
 * `FragmentManager`, the `FragmentStateAdapter`, the two `BaseExpandableListAdapter`s and the four
 * public setters removed.
 *
 * Tab ↔ pager sync is two `LaunchedEffect`s and the index is **state**, not composable-local, so it
 * survives rotation and arrives correct from the route argument on the first frame.
 */
@Composable
internal fun PermissionManagerScreen(
    state: PermissionManagerState,
    onIntent: (PermissionManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.permission_manager_title),
                    onBack = { onIntent(PermissionManagerIntent.BackPressed) },
                )
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(PermissionManagerIntent.RetryTapped) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                AnimatedContent(targetState = state.isScanning, label = "permission-scan") { scanning ->
                    if (scanning) {
                        ScanStage(onIntent)
                    } else {
                        TabbedContent(state, onIntent)
                    }
                }
            }
            DetailSheetHost(state, onIntent)
        }
    }
}

/**
 * The scan stage. `isScanning` is lowered **only** by `ScanAnimationFinished`, and this `LaunchedEffect`
 * is the timeout that fires it if the animation never completes — the same shape as `ToolOverlay`, and
 * the reason the reveal is an Intent rather than an animation callback (`LLM.md` §7.1). The competitor
 * reveals its list from an ad SDK's close callback.
 */
@Composable
private fun ScanStage(onIntent: (PermissionManagerIntent) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LoadingOverlay(label = stringResource(R.string.permission_manager_scanning))
    }
    LaunchedEffect(Unit) { onIntent(PermissionManagerIntent.ScanAnimationFinished) }
}

@Composable
private fun TabbedContent(
    state: PermissionManagerState,
    onIntent: (PermissionManagerIntent) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.ordinal,
        pageCount = { PermissionTab.entries.size },
    )

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab.ordinal) {
            pagerState.animateScrollToPage(state.selectedTab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            PermissionTab.entries.getOrNull(page)?.let { tab ->
                if (tab != state.selectedTab) onIntent(PermissionManagerIntent.TabSelected(tab))
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            PermissionTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.selectedTab,
                    onClick = { onIntent(PermissionManagerIntent.TabSelected(tab)) },
                    text = { Text(stringResource(tab.labelRes())) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (PermissionTab.entries[page]) {
                PermissionTab.Apps -> AppsTab(state.apps, onIntent)
                PermissionTab.Sensitive ->
                    SensitiveTab(state.visibleGroups, state.expandedGroups, onIntent)

                PermissionTab.SpecialAccess -> SpecialAccessTab(state.specialAccess, onIntent)
            }
        }
    }
}

/**
 * `AppBottomSheet` from `:core:ui`, so scrim, dismiss gesture and back handling come for free. The
 * competitor hand-builds all three, on top of a show/hide `FragmentTransaction` whose only re-open
 * callback is `onHiddenChanged` (§4.5).
 */
@Composable
private fun DetailSheetHost(
    state: PermissionManagerState,
    onIntent: (PermissionManagerIntent) -> Unit,
) {
    val sheet = state.detailSheet ?: return
    val report = state.detailApp ?: return
    AppBottomSheet(onDismissRequest = { onIntent(PermissionManagerIntent.DetailDismissed) }) {
        AppPermissionDetail(report, sheet.expandedSections, onIntent)
    }
}

private fun PermissionTab.labelRes(): Int = when (this) {
    PermissionTab.Apps -> R.string.permission_manager_tab_apps
    PermissionTab.Sensitive -> R.string.permission_manager_tab_sensitive
    PermissionTab.SpecialAccess -> R.string.permission_manager_tab_special
}
