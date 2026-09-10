package com.pion.phonecleaner.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SelectionBar
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.component.state.LoadingOverlay
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenGutter
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.trash.component.TrashEntryRow
import com.pion.phonecleaner.feature.trash.component.TrashUnavailableCard
import com.pion.phonecleaner.core.ui.R as CoreUiR

/**
 * `docs/android-mvi-best-practices.md` §4. Stateless `(state, onIntent) -> Unit`; `onIntent` is passed
 * down as-is everywhere below, including into [TrashEntryRow] (`LLM.md` §8).
 */
@Composable
internal fun TrashScreen(
    state: TrashState,
    onIntent: (TrashIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.trash_title),
                    onBack = { onIntent(TrashIntent.BackPressed) },
                    actionLabel = if (state.canEmptyBin) stringResource(R.string.trash_action_empty) else null,
                    actionIcon = if (state.canEmptyBin) Icons.Filled.DeleteSweep else null,
                    onAction = if (state.canEmptyBin) {
                        { onIntent(TrashIntent.EmptyBinPressed) }
                    } else {
                        null
                    },
                    bannerSlot = { TrashRetentionNote() },
                )
                if (!state.isTrashAvailable) {
                    TrashUnavailableCard(onAllowAccess = { onIntent(TrashIntent.AllowAccessPressed) })
                }
                TrashTabs(state = state, onIntent = onIntent)
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(TrashIntent.ScreenStarted) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                if (state.isEmpty) {
                    EmptyState(message = stringResource(R.string.trash_empty))
                } else {
                    TrashEntryList(state, onIntent, Modifier.weight(1f))
                }
                // The riskier of the two selection actions: a plain TextButton above SelectionBar's
                // own single, more prominent action slot (LLM.md §4's promotion rule does not apply —
                // this stays cluster-private, one screen wide).
                if (state.selectedCount > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth().screenGutter().padding(vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { onIntent(TrashIntent.DeleteForeverPressed) },
                            enabled = state.canAct,
                        ) { Text(stringResource(R.string.trash_action_delete_forever)) }
                    }
                }
                if (state.visibleEntries.isNotEmpty()) {
                    SelectionBar(
                        selectedCount = state.selectedCount,
                        selectedBytes = state.selectedBytes,
                        onSelectAll = { onIntent(TrashIntent.SelectAllToggled) },
                        onDelete = { onIntent(TrashIntent.RestorePressed) },
                        actionLabel = stringResource(R.string.trash_action_restore),
                        enabled = state.canAct,
                    )
                }
            }
            // `now` defaults to `Instant.DISTANT_PAST`, which would render every countdown as expired
            // for one frame — this is why `phase` starts at `Loading` and the list stays covered until
            // the first `observeTrash()`/reconcile tick lands (TrashContract.kt's own KDoc on `now`).
            if (state.phase == TrashPhase.Loading) LoadingOverlay()
        }
    }
    TrashConfirmDialog(
        spec = state.confirm,
        onConfirm = { onIntent(TrashIntent.ConfirmAccepted) },
        onDismiss = { onIntent(TrashIntent.ConfirmDismissed) },
    )
}

@Composable
private fun TrashRetentionNote(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.trash_retention_note),
        modifier = modifier.screenGutter().padding(bottom = Spacing.xs),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TrashEntryList(
    state: TrashState,
    onIntent: (TrashIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
    ) {
        items(
            items = state.visibleEntries,
            // `TrashEntry.id` is the identity the selection Set, the row key and the restore/delete
            // requests all carry.
            key = { it.id },
            contentType = { "trash" },
        ) { entry ->
            TrashEntryRow(
                entry = entry,
                label = entry.expiryLabel(state.now),
                isSelected = entry.id in state.selectedIds,
                onIntent = onIntent,
            )
        }
    }
}

@Composable
private fun TrashTabs(state: TrashState, onIntent: (TrashIntent) -> Unit) {
    TabRow(selectedTabIndex = state.selectedTab.ordinal) {
        Tab(
            selected = state.selectedTab == TrashTab.Original,
            onClick = { onIntent(TrashIntent.TabSelected(TrashTab.Original)) },
            text = { Text(stringResource(R.string.trash_tab_original)) },
        )
        Tab(
            selected = state.selectedTab == TrashTab.Zip,
            onClick = { onIntent(TrashIntent.TabSelected(TrashTab.Zip)) },
            text = { Text(stringResource(R.string.trash_tab_zip)) },
        )
    }
}

/**
 * One host for the two confirms this screen raises. Shape copied from `feature/files/component/
 * ConfirmDialogHost.kt` — cannot be imported directly, `:feature:trash` may not depend on
 * `:feature:files` (`LLM.md` §2).
 */
@Composable
private fun TrashConfirmDialog(spec: ConfirmSpec?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    if (spec == null) return
    AppDialog(
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(spec.confirmRes),
        onConfirm = onConfirm,
        title = stringResource(spec.titleRes),
        body = pluralStringResource(spec.bodyRes, spec.count, spec.count),
        dismissLabel = stringResource(CoreUiR.string.action_cancel),
        onDismiss = onDismiss,
    )
}
