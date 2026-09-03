package com.pion.phonecleaner.feature.files.duplicates

import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.component.ConfirmDialogHost
import com.pion.phonecleaner.feature.files.component.ToolBanner
import com.pion.phonecleaner.feature.files.component.ToolOverlay
import com.pion.phonecleaner.feature.files.component.TruncationBanner
import com.pion.phonecleaner.feature.files.duplicates.component.DuplicateGroupHeader
import com.pion.phonecleaner.feature.files.duplicates.component.DuplicatePreviewSheet
import com.pion.phonecleaner.feature.files.duplicates.component.DuplicateRow

/**
 * `docs/screens/14-file-tools-and-app-manager.md` §2.3.
 *
 * `onIntent` is passed down as-is: a lambda allocated inside `items {}` is a new instance every
 * recomposition and defeats the skip for every row (`LLM.md` §8).
 */
@Composable
internal fun DuplicatesScreen(
    state: DuplicatesState,
    onIntent: (DuplicatesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.duplicates_title),
                    onBack = { onIntent(DuplicatesIntent.BackPressed) },
                    actionLabel = stringResource(R.string.duplicates_deselect_all),
                    onAction = { onIntent(DuplicatesIntent.DeselectAllPressed) },
                )
                TruncationBanner(state.scanTruncated)
                if (state.failedCount > 0) {
                    ToolBanner(stringResource(R.string.files_delete_failed, state.failedCount))
                }
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(DuplicatesIntent.ScreenStarted) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                if (state.showEmptyState) {
                    EmptyState(message = stringResource(R.string.duplicates_empty))
                } else {
                    ReclaimableBanner(state)
                    DuplicateGroupList(state, onIntent, Modifier.weight(1f))
                }
                if (state.groups.isNotEmpty()) {
                    SelectionBar(
                        selectedCount = state.selectedCount,
                        selectedBytes = state.selectedBytes,
                        onSelectAll = { onIntent(DuplicatesIntent.SelectAllOlderPressed) },
                        onDelete = { onIntent(DuplicatesIntent.DeletePressed) },
                    )
                }
            }
            ToolOverlay(
                phase = state.phase,
                onCompletionFinished = { onIntent(DuplicatesIntent.CompletionAnimationFinished) },
                scanningLabel = stringResource(
                    R.string.duplicates_hashing,
                    state.hashed,
                    state.candidates,
                ),
                onCancel = if (state.phase == ToolPhase.Scanning) {
                    { onIntent(DuplicatesIntent.BackPressed) }
                } else {
                    null
                },
            )
        }
    }
    ConfirmDialogHost(
        spec = state.confirm,
        onConfirm = { onIntent(DuplicatesIntent.DeleteConfirmed) },
        onDismiss = { onIntent(DuplicatesIntent.DeleteDismissed) },
    )
    state.previewing?.let { file ->
        DuplicatePreviewSheet(
            file = file,
            onOpen = { onIntent(DuplicatesIntent.PreviewConfirmed) },
            onDismiss = { onIntent(DuplicatesIntent.PreviewDismissed) },
        )
    }
}

/** What keeping one copy of each group would recover — the number the whole screen is about. */
@Composable
private fun ReclaimableBanner(state: DuplicatesState) {
    if (state.phase != ToolPhase.Ready || state.groups.isEmpty()) return
    val bytes = rememberByteFormat()
    ToolBanner(
        stringResource(R.string.duplicates_reclaimable, bytes.size(state.reclaimableBytes).toString()),
    )
}

/**
 * One `LazyColumn`; each group contributes a `stickyHeader` keyed on its digest and its members as
 * keyed items. The digest is the group's identity — the competitor computes it and throws it away
 * (§2.4).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DuplicateGroupList(
    state: DuplicatesState,
    onIntent: (DuplicatesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
    ) {
        state.groups.forEach { group ->
            stickyHeader(key = "h:${group.md5}", contentType = GroupHeaderType) {
                DuplicateGroupHeader(
                    copies = group.files.size,
                    sizeBytes = group.files.firstOrNull()?.sizeBytes ?: 0L,
                )
            }
            items(
                items = group.files,
                key = { it.id },
                contentType = { RowType },
            ) { file ->
                DuplicateRow(
                    file = file,
                    selected = file.id in state.selectedIds,
                    isKept = file.id == group.newestId,
                    onIntent = onIntent,
                )
            }
        }
    }
}

private const val GroupHeaderType = "duplicates.header"
private const val RowType = "duplicates.row"
