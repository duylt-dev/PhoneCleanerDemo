package com.pion.phonecleaner.feature.files.audio

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
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.FolderFilterBar
import com.pion.phonecleaner.core.ui.component.list.SelectionBar
import com.pion.phonecleaner.core.ui.component.list.SortMenuItem
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.component.ConfirmDialogHost
import com.pion.phonecleaner.feature.files.component.FileRow
import com.pion.phonecleaner.feature.files.component.MediaPermissionState
import com.pion.phonecleaner.feature.files.component.MediaSort
import com.pion.phonecleaner.feature.files.component.ToolBanner
import com.pion.phonecleaner.feature.files.component.ToolOverlay
import com.pion.phonecleaner.feature.files.component.TruncationBanner
import com.pion.phonecleaner.feature.files.component.folderTabs

/**
 * `docs/screens/14-file-tools-and-app-manager.md` §4.
 *
 * A `LazyColumn`, not a grid. There is **no partial-access banner**: `READ_MEDIA_AUDIO` has no
 * user-selected variant, so that state cannot occur here (§4).
 */
@Composable
internal fun AudioManagerScreen(
    state: AudioManagerState,
    onIntent: (AudioManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.audio_title),
                    onBack = { onIntent(AudioManagerIntent.BackPressed) },
                )
                FolderFilterBar(
                    tabs = state.files.items.folderTabs(stringResource(R.string.media_folder_all)),
                    selectedKey = state.selectedFolderPath,
                    sort = state.sort,
                    sortItems = audioSortItems(),
                    onFolderSelected = { onIntent(AudioManagerIntent.FolderSelected(it)) },
                    onSortSelected = { onIntent(AudioManagerIntent.SortSelected(it)) },
                )
                TruncationBanner(state.scanTruncated)
                if (state.failedCount > 0) {
                    ToolBanner(stringResource(R.string.files_delete_failed, state.failedCount))
                }
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(AudioManagerIntent.ScreenStarted) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                when {
                    state.showPermissionState -> MediaPermissionState(
                        onGrant = { onIntent(AudioManagerIntent.GrantPressed) },
                    )

                    state.showEmptyState -> EmptyState(stringResource(R.string.audio_empty))
                    else -> AudioList(state, onIntent, Modifier.weight(1f))
                }
                if (state.files.items.isNotEmpty()) {
                    SelectionBar(
                        selectedCount = state.selectedCount,
                        selectedBytes = state.selectedBytes,
                        onSelectAll = { onIntent(AudioManagerIntent.SelectAllToggled) },
                        onDelete = { onIntent(AudioManagerIntent.DeletePressed) },
                    )
                }
            }
            ToolOverlay(
                phase = state.phase,
                onCompletionFinished = {
                    onIntent(AudioManagerIntent.CompletionAnimationFinished)
                },
                scanningLabel = stringResource(R.string.files_scanning),
            )
        }
    }
    ConfirmDialogHost(
        spec = state.confirm,
        onConfirm = { onIntent(AudioManagerIntent.DeleteConfirmed) },
        onDismiss = { onIntent(AudioManagerIntent.DeleteDismissed) },
    )
}

@Composable
private fun AudioList(
    state: AudioManagerState,
    onIntent: (AudioManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
    ) {
        items(
            items = state.visibleItems,
            key = { it.id },
            contentType = { it.kind },
        ) { file ->
            AudioRow(
                file = file,
                selected = file.id in state.files.selectedIds,
                onIntent = onIntent,
            )
        }
    }
}

/**
 * `onIntent` is passed into the item as-is; the two `() -> Unit` callbacks are `remember`ed on the
 * row's own identity, so the row stays skippable (`LLM.md` §8).
 */
@Composable
private fun AudioRow(
    file: ScannedFile,
    selected: Boolean,
    onIntent: (AudioManagerIntent) -> Unit,
) {
    val id = file.id
    val onToggle = remember(id, onIntent) { { onIntent(AudioManagerIntent.RowToggled(id)) } }
    val onOpen = remember(id, onIntent) { { onIntent(AudioManagerIntent.RowOpened(id)) } }
    FileRow(file = file, selected = selected, onToggle = onToggle, onOpen = onOpen)
}

@Composable
private fun audioSortItems(): List<SortMenuItem<MediaSort>> = listOf(
    SortMenuItem(MediaSort.NewestFirst, stringResource(R.string.media_sort_newest)),
    SortMenuItem(MediaSort.LargestFirst, stringResource(R.string.media_sort_largest)),
    SortMenuItem(MediaSort.Name, stringResource(R.string.media_sort_name)),
)
