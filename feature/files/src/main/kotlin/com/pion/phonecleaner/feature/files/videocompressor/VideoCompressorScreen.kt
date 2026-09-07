package com.pion.phonecleaner.feature.files.videocompressor

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SelectionBar
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.component.MediaPermissionState
import com.pion.phonecleaner.feature.files.component.MediaSortChips
import com.pion.phonecleaner.feature.files.component.PartialAccessBanner
import com.pion.phonecleaner.feature.files.component.ToolOverlay
import com.pion.phonecleaner.feature.files.video.component.VideoCell
import com.pion.phonecleaner.feature.files.videocompressor.component.AlreadyCompressedBadge
import com.pion.phonecleaner.feature.files.videocompressor.component.VideoCodecChips
import com.pion.phonecleaner.feature.files.videocompressor.component.VideoCompressorIntroPanel
import com.pion.phonecleaner.feature.files.videocompressor.component.VideoEstimateLine
import com.pion.phonecleaner.feature.files.videocompressor.component.VideoPresetChips

/**
 * `videocompressor` — `PhotoCompressorScreen`'s job with `VideoManagerScreen`'s plumbing (phase-06
 * overview). The permission funnel, sort, selection and grid come from this cluster's own
 * `component/` package; the preset/codec chips and the estimate line are this screen's own.
 */
@Composable
internal fun VideoCompressorScreen(
    state: VideoCompressorState,
    onIntent: (VideoCompressorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.video_compress_title),
                    onBack = { onIntent(VideoCompressorIntent.BackPressed) },
                )
                MediaSortChips(
                    sort = state.sort,
                    onSelect = { onIntent(VideoCompressorIntent.SortSelected(it)) },
                )
                VideoPresetChips(
                    selected = state.preset,
                    onSelect = { onIntent(VideoCompressorIntent.PresetSelected(it)) },
                )
                VideoCodecChips(
                    selected = state.codec,
                    isHevcAvailable = state.isHevcAvailable,
                    onSelect = { onIntent(VideoCompressorIntent.CodecSelected(it)) },
                )
                PartialAccessBanner(
                    visible = state.showPartialAccessBanner,
                    onGrantMore = { onIntent(VideoCompressorIntent.GrantMorePressed) },
                )
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(VideoCompressorIntent.ScreenStarted) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                when {
                    state.showPermissionState -> MediaPermissionState(
                        onGrant = { onIntent(VideoCompressorIntent.GrantMorePressed) },
                    )

                    state.showEmptyState ->
                        EmptyState(stringResource(R.string.video_compress_empty))

                    else -> VideoCompressorGrid(state, onIntent, Modifier.weight(1f))
                }
                VideoEstimateLine(
                    estimate = state.estimate,
                    modifier = Modifier.padding(horizontal = ScreenGutter, vertical = Spacing.sm),
                )
                if (state.videos.items.isNotEmpty()) {
                    SelectionBar(
                        selectedCount = state.selectedCount,
                        selectedBytes = state.selectedBytes,
                        onSelectAll = { onIntent(VideoCompressorIntent.SelectAllToggled) },
                        onDelete = { onIntent(VideoCompressorIntent.ContinuePressed) },
                        actionLabel = stringResource(R.string.video_compress_continue),
                        enabled = state.canContinue,
                    )
                }
            }
            ToolOverlay(
                phase = state.phase,
                onCompletionFinished = { onIntent(VideoCompressorIntent.CompletionAnimationFinished) },
                scanningLabel = stringResource(R.string.files_scanning),
            )
            if (state.introVisible) {
                VideoCompressorIntroPanel(onStart = { onIntent(VideoCompressorIntent.StartPressed) })
            }
        }
    }
}

@Composable
private fun VideoCompressorGrid(
    state: VideoCompressorState,
    onIntent: (VideoCompressorIntent) -> Unit,
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
            items = state.videos.items,
            key = { it.id },
            contentType = { it.file.kind },
        ) { candidate ->
            VideoCompressorGridCell(
                candidate = candidate,
                selected = candidate.id in state.videos.selectedIds,
                onIntent = onIntent,
            )
        }
    }
}

/**
 * `onIntent` is not passed into the row; only the two `remember`ed callbacks the row needs are — the
 * `VideoGridCell` precedent in `VideoManagerScreen` copied verbatim (`LLM.md` §8). This picker raises
 * no "open" effect, so both `VideoCell` callbacks resolve to the same toggle: choosing a video IS the
 * action here.
 */
@Composable
private fun VideoCompressorGridCell(
    candidate: VideoCandidate,
    selected: Boolean,
    onIntent: (VideoCompressorIntent) -> Unit,
) {
    val id = candidate.id
    val onToggle = remember(id, onIntent) { { onIntent(VideoCompressorIntent.RowToggled(id)) } }
    Box {
        VideoCell(file = candidate.file, selected = selected, onToggle = onToggle, onOpen = onToggle)
        if (candidate.alreadyCompressed) {
            AlreadyCompressedBadge(Modifier.align(Alignment.BottomStart))
        }
    }
}

/** Three columns, matching `VideoManagerScreen`. */
private const val GridColumns = 3
