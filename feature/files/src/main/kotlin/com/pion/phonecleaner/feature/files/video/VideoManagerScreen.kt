package com.pion.phonecleaner.feature.files.video

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.pion.phonecleaner.feature.files.component.MediaPermissionState
import com.pion.phonecleaner.feature.files.component.MediaSort
import com.pion.phonecleaner.feature.files.component.PartialAccessBanner
import com.pion.phonecleaner.feature.files.component.ToolBanner
import com.pion.phonecleaner.feature.files.component.ToolOverlay
import com.pion.phonecleaner.feature.files.component.TruncationBanner
import com.pion.phonecleaner.feature.files.component.folderTabs
import com.pion.phonecleaner.feature.files.video.component.VideoCell

/**
 * `docs/screens/14-file-tools-and-app-manager.md` §3.3.
 *
 * `LazyVerticalGrid` replaces `Exoduhis`, a self-measuring `GridLayoutManager` subclass — **and the
 * grid is the outer scrollable, never nested inside another one**, which is the shape that forces a
 * lazy list to measure every child.
 */
@Composable
internal fun VideoManagerScreen(
    state: VideoManagerState,
    onIntent: (VideoManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.video_title),
                    onBack = { onIntent(VideoManagerIntent.BackPressed) },
                )
                FolderFilterBar(
                    tabs = state.files.items.folderTabs(stringResource(R.string.media_folder_all)),
                    selectedKey = state.selectedFolderPath,
                    sort = state.sort,
                    sortItems = videoSortItems(),
                    onFolderSelected = { onIntent(VideoManagerIntent.FolderSelected(it)) },
                    onSortSelected = { onIntent(VideoManagerIntent.SortSelected(it)) },
                )
                PartialAccessBanner(
                    visible = state.showPartialAccessBanner,
                    onGrantMore = { onIntent(VideoManagerIntent.GrantMorePressed) },
                )
                TruncationBanner(state.scanTruncated)
                if (state.failedCount > 0) {
                    ToolBanner(stringResource(R.string.files_delete_failed, state.failedCount))
                }
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(VideoManagerIntent.ScreenStarted) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                when {
                    state.showPermissionState -> MediaPermissionState(
                        onGrant = { onIntent(VideoManagerIntent.GrantMorePressed) },
                    )

                    state.showEmptyState -> EmptyState(stringResource(R.string.video_empty))
                    else -> VideoGrid(state, onIntent, Modifier.weight(1f))
                }
                if (state.files.items.isNotEmpty()) {
                    SelectionBar(
                        selectedCount = state.selectedCount,
                        selectedBytes = state.selectedBytes,
                        onSelectAll = { onIntent(VideoManagerIntent.SelectAllToggled) },
                        onDelete = { onIntent(VideoManagerIntent.DeletePressed) },
                    )
                }
            }
            ToolOverlay(
                phase = state.phase,
                onCompletionFinished = {
                    onIntent(VideoManagerIntent.CompletionAnimationFinished)
                },
                scanningLabel = stringResource(R.string.files_scanning),
            )
        }
    }
    ConfirmDialogHost(
        spec = state.confirm,
        onConfirm = { onIntent(VideoManagerIntent.DeleteConfirmed) },
        onDismiss = { onIntent(VideoManagerIntent.DeleteDismissed) },
    )
}

@Composable
private fun VideoGrid(
    state: VideoManagerState,
    onIntent: (VideoManagerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GridColumns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = ScreenGutter,
            end = ScreenGutter,
            bottom = PageSpacing.listBottom,
        ),
    ) {
        items(
            items = state.visibleItems,
            key = { it.id },
            contentType = { it.kind },
        ) { file ->
            VideoGridCell(
                file = file,
                selected = file.id in state.files.selectedIds,
                onIntent = onIntent,
            )
        }
    }
}

/**
 * `onIntent` is passed into the item as-is; the two `() -> Unit` callbacks are `remember`ed on the
 * row's own identity, so no new instance is allocated per recomposition and the cell stays skippable
 * (`LLM.md` §8). Writing them inline inside `items { }` defeats the skip for every cell.
 */
@Composable
private fun VideoGridCell(
    file: ScannedFile,
    selected: Boolean,
    onIntent: (VideoManagerIntent) -> Unit,
) {
    val id = file.id
    val onToggle = remember(id, onIntent) { { onIntent(VideoManagerIntent.RowToggled(id)) } }
    val onOpen = remember(id, onIntent) { { onIntent(VideoManagerIntent.RowOpened(id)) } }
    VideoCell(file = file, selected = selected, onToggle = onToggle, onOpen = onOpen)
}

/** Three columns, as §3.3 specifies. */
private const val GridColumns = 3

@Composable
private fun videoSortItems(): List<SortMenuItem<MediaSort>> = listOf(
    SortMenuItem(MediaSort.NewestFirst, stringResource(R.string.media_sort_newest)),
    SortMenuItem(MediaSort.LargestFirst, stringResource(R.string.media_sort_largest)),
    SortMenuItem(MediaSort.Name, stringResource(R.string.media_sort_name)),
)
