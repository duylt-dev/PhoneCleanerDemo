package com.pion.phonecleaner.feature.applock.applock

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.applock.R
import com.pion.phonecleaner.feature.applock.applock.component.AppLockPermissionSheet
import com.pion.phonecleaner.feature.applock.applock.component.AppLockRow
import com.pion.phonecleaner.feature.applock.applock.component.AppLockScanStage
import com.pion.phonecleaner.feature.applock.applock.component.UnlockConfirmSheet

/**
 * The App Lock home (`docs/screens/16-app-lock.md` §1.3). `SemantanActivity` + two fragments + an
 * adapter + `Chaennia`'s three `MutableLiveData` become this one stateless composable.
 *
 * The tab index is on `AppLockState`, not composable-local: the competitor keeps it in an Activity
 * field that dies on rotation, along with the adapter and every dialog, so a rotate restarts a
 * four-second scan (§1.5).
 */
@Composable
internal fun AppLockScreen(
    state: AppLockState,
    onIntent: (AppLockIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.screenInsetsPadding(),
            topBar = {
                PageHeader(
                    title = stringResource(R.string.app_lock_title),
                    onBack = { onIntent(AppLockIntent.BackPressed) },
                    actionLabel = stringResource(R.string.app_lock_settings_action),
                    actionIcon = Icons.Filled.Settings,
                    onAction = { onIntent(AppLockIntent.SettingsTapped) },
                )
            },
        ) { padding ->
            AnimatedContent(
                targetState = state.isScanning,
                modifier = Modifier.padding(padding),
                label = "appLockScanStage",
            ) { isScanning ->
                if (isScanning) {
                    AppLockScanStage(
                        onFinished = { onIntent(AppLockIntent.ScanAnimationFinished) },
                    )
                } else {
                    AppLockPages(state, onIntent)
                }
            }
        }
    }
    if (state.isPermissionSheetVisible) AppLockPermissionSheet(state, onIntent)
    state.pendingUnlockApp?.let { UnlockConfirmSheet(it, onIntent) }
}

/**
 * Two pages of one list. Tab ↔ pager sync is two `LaunchedEffect`s over the two sources of truth,
 * so a swipe and a tab tap end in the same place.
 */
@Composable
private fun AppLockPages(state: AppLockState, onIntent: (AppLockIntent) -> Unit) {
    val pagerState = rememberPagerState(
        initialPage = state.selectedTab.ordinal,
        pageCount = { AppLockTab.entries.size },
    )
    LaunchedEffect(state.selectedTab) { pagerState.animateScrollToPage(state.selectedTab.ordinal) }
    LaunchedEffect(pagerState.currentPage) {
        val tab = AppLockTab.entries[pagerState.currentPage]
        if (tab != state.selectedTab) onIntent(AppLockIntent.TabSelected(tab))
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            AppLockTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.selectedTab,
                    onClick = { onIntent(AppLockIntent.TabSelected(tab)) },
                    text = { Text(tab.label(state.lockedCount)) },
                )
            }
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            AppLockList(
                state = state,
                tab = AppLockTab.entries[page],
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun AppLockList(
    state: AppLockState,
    tab: AppLockTab,
    onIntent: (AppLockIntent) -> Unit,
) {
    val apps = if (tab == AppLockTab.All) state.apps else state.lockedApps
    if (apps.isEmpty()) {
        EmptyState(
            message = stringResource(
                if (tab == AppLockTab.All) R.string.app_lock_empty_all
                else R.string.app_lock_empty_locked,
            ),
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
    ) {
        items(
            items = apps,
            key = { it.packageName },
            contentType = { RowContentType },
        ) { app ->
            // `onIntent` is passed down AS IS. A per-row lambda allocated inside `items {}` is a new
            // instance every recomposition and defeats the skip for every row (`LLM.md` §8).
            AppLockRow(
                app = app,
                isBusy = app.packageName in state.togglingPackages,
                onIntent = onIntent,
            )
        }
    }
}

/** The count is on the tab, over a list the repository has already reconciled (§1.1). */
@Composable
private fun AppLockTab.label(lockedCount: Int): String = when (this) {
    AppLockTab.All -> stringResource(R.string.app_lock_tab_all)
    AppLockTab.Locked -> if (lockedCount > 0) {
        stringResource(R.string.app_lock_tab_locked_count, lockedCount)
    } else {
        stringResource(R.string.app_lock_tab_locked)
    }
}

private const val RowContentType = "appLockRow"
