package com.pion.phonecleaner.feature.files.appmanager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SelectionBar
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.appmanager.component.AppRow
import com.pion.phonecleaner.feature.files.appmanager.component.SortChipRow
import com.pion.phonecleaner.feature.files.appmanager.component.TotalsHeader
import com.pion.phonecleaner.feature.files.appmanager.component.UninstallProgressBar
import com.pion.phonecleaner.feature.files.appmanager.component.UsageAccessRationaleSheet
import com.pion.phonecleaner.feature.files.component.ConfirmDialogHost
import com.pion.phonecleaner.feature.files.component.ToolBanner
import com.pion.phonecleaner.feature.files.component.ToolOverlay

/**
 * `docs/screens/14-file-tools-and-app-manager.md` §5.3.
 *
 * `ManagedApp` is `@Immutable` and the selection is a `Set<String>` off the model, so `AppRow` is
 * skippable and a tap recomposes one row. `onIntent` is passed down as-is (`LLM.md` §8).
 */
@Composable
internal fun AppManagerScreen(
    state: AppManagerState,
    onIntent: (AppManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.app_manager_title),
                    onBack = { onIntent(AppManagerIntent.BackPressed) },
                )
                TotalsHeader(bytes = state.totalBytes, count = state.apps.size)
                SortChipRow(
                    sort = state.sort,
                    lastUsedEnabled = state.lastUsedSortEnabled,
                    onSelect = { onIntent(AppManagerIntent.SortSelected(it)) },
                )
                if (state.usageAccess == UsageAccess.Denied) {
                    ToolBanner(
                        message = stringResource(R.string.app_manager_usage_access_body),
                        actionLabel = stringResource(R.string.app_manager_usage_access_grant),
                        onAction = { onIntent(AppManagerIntent.GrantUsageAccessPressed) },
                    )
                }
                state.uninstalling?.let { UninstallProgressBar(it) }
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(AppManagerIntent.ScreenStarted) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                if (state.showEmptyState) {
                    EmptyState(message = stringResource(R.string.app_manager_empty))
                } else {
                    AppList(state, onIntent, Modifier.weight(1f))
                }
                if (state.apps.isNotEmpty()) {
                    SelectionBar(
                        selectedCount = state.selectedCount,
                        selectedBytes = state.selectedBytes,
                        onSelectAll = { onIntent(AppManagerIntent.SelectAllToggled) },
                        onDelete = { onIntent(AppManagerIntent.UninstallPressed) },
                        actionLabel = stringResource(R.string.app_manager_uninstall_action),
                    )
                }
            }
            ToolOverlay(
                phase = state.phase,
                onCompletionFinished = { onIntent(AppManagerIntent.CompletionAnimationFinished) },
                scanningLabel = stringResource(
                    R.string.app_manager_sizing_progress,
                    state.sizedCount,
                    state.apps.size,
                ),
                onCancel = if (state.phase == ToolPhase.Scanning) {
                    { onIntent(AppManagerIntent.BackPressed) }
                } else {
                    null
                },
            )
        }
    }
    ConfirmDialogHost(
        spec = state.confirm,
        onConfirm = { onIntent(AppManagerIntent.UninstallConfirmed) },
        onDismiss = { onIntent(AppManagerIntent.UninstallDismissed) },
    )
    if (state.rationale) {
        UsageAccessRationaleSheet(
            onContinue = { onIntent(AppManagerIntent.RationaleContinued) },
            onDismiss = { onIntent(AppManagerIntent.RationaleDismissed) },
        )
    }
}

@Composable
private fun AppList(
    state: AppManagerState,
    onIntent: (AppManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
    ) {
        items(
            items = state.apps,
            // The package name is the identity the selection, the uninstall queue and this key
            // all carry.
            key = { it.packageName },
            contentType = { AppRowType },
        ) { app ->
            AppRow(
                app = app,
                selected = app.packageName in state.selectedPackages,
                // The row cannot read `0` correctly without it: no grant means "not measured",
                // the grant means "not opened inside the window". A `Boolean` keeps the row skippable.
                usageAccessGranted = state.usageAccessGranted,
                onIntent = onIntent,
            )
        }
    }
}

private const val AppRowType = "appmanager.row"
