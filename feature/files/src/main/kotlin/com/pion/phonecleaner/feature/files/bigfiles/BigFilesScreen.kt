package com.pion.phonecleaner.feature.files.bigfiles

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
import androidx.compose.runtime.remember
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
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.component.ConfirmDialogHost
import com.pion.phonecleaner.feature.files.component.CoverageBanner
import com.pion.phonecleaner.feature.files.component.FileRow
import com.pion.phonecleaner.feature.files.component.ToolBanner
import com.pion.phonecleaner.feature.files.component.ToolOverlay
import com.pion.phonecleaner.feature.files.component.TruncationBanner

/**
 * `docs/screens/14-file-tools-and-app-manager.md` §1.3.
 *
 * One keyed `LazyColumn` replaces an adapter plus `notifyDataSetChanged()` on **every tap** — O(1)
 * recomposition instead of O(n). `onIntent` is passed down as-is: a lambda allocated inside `items {}`
 * is a new instance every recomposition and defeats the skip for every row (`LLM.md` §8).
 */
@Composable
internal fun BigFilesScreen(
    state: BigFilesState,
    onIntent: (BigFilesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.big_files_title),
                    onBack = { onIntent(BigFilesIntent.BackPressed) },
                )
                CoverageBanner(
                    coverage = state.coverage,
                    onGrantMore = { onIntent(BigFilesIntent.GrantMoreAccessPressed) },
                )
                TruncationBanner(state.scanTruncated)
                if (state.failedCount > 0) {
                    ToolBanner(stringResource(R.string.files_delete_failed, state.failedCount))
                }
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(BigFilesIntent.ScreenStarted) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                if (state.showEmptyState) {
                    EmptyState(message = stringResource(R.string.big_files_empty))
                } else {
                    BigFileList(state, onIntent, Modifier.weight(1f))
                }
                if (state.files.items.isNotEmpty()) {
                    SelectionBar(
                        selectedCount = state.selectedCount,
                        selectedBytes = state.selectedBytes,
                        onSelectAll = { onIntent(BigFilesIntent.SelectAllToggled) },
                        onDelete = { onIntent(BigFilesIntent.DeletePressed) },
                    )
                }
            }
            ToolOverlay(
                phase = state.phase,
                onCompletionFinished = { onIntent(BigFilesIntent.CompletionAnimationFinished) },
                scanningLabel = stringResource(R.string.files_scanning_count, state.scannedCount),
                onCancel = if (state.phase == ToolPhase.Scanning) {
                    { onIntent(BigFilesIntent.BackPressed) }
                } else {
                    null
                },
            )
        }
    }
    ConfirmDialogHost(
        spec = state.confirm,
        onConfirm = { onIntent(BigFilesIntent.DeleteConfirmed) },
        onDismiss = { onIntent(BigFilesIntent.DeleteDismissed) },
    )
}

@Composable
private fun BigFileList(
    state: BigFilesState,
    onIntent: (BigFilesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
    ) {
        items(
            items = state.files.items,
            // `ScannedFile.id` is the identity a selection, a delete request and this key all carry.
            key = { it.id },
            contentType = { it.kind },
        ) { file ->
            BigFileRow(
                file = file,
                selected = file.id in state.files.selectedIds,
                onIntent = onIntent,
            )
        }
    }
}

/**
 * `onIntent` is passed into the item as-is; the two `() -> Unit` callbacks `FileRow` takes are
 * `remember`ed on the row's own identity, so no new instance is allocated per recomposition and the
 * row stays skippable (`LLM.md` §8). Writing them inline inside `items { }` is the shape that defeats
 * the skip for every row in the list.
 */
@Composable
private fun BigFileRow(
    file: ScannedFile,
    selected: Boolean,
    onIntent: (BigFilesIntent) -> Unit,
) {
    val id = file.id
    val onToggle = remember(id, onIntent) { { onIntent(BigFilesIntent.RowToggled(id)) } }
    val onOpen = remember(id, onIntent) { { onIntent(BigFilesIntent.RowOpened(id)) } }
    FileRow(file = file, selected = selected, onToggle = onToggle, onOpen = onOpen)
}
