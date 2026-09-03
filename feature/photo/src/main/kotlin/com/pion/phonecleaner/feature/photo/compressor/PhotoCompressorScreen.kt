package com.pion.phonecleaner.feature.photo.compressor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.component.PHOTO_GRID_COLUMNS
import com.pion.phonecleaner.feature.photo.component.PhotoCompletionPanel
import com.pion.phonecleaner.feature.photo.component.PhotoScanPanel
import com.pion.phonecleaner.feature.photo.component.photoGroupItems
import com.pion.phonecleaner.feature.photo.compressor.component.CompressionEstimateRow
import com.pion.phonecleaner.feature.photo.compressor.component.CompressorIntroPanel

/**
 * `docs/screens/13-photo-and-media.md` §3.3.
 *
 * Either the landing panel or the grid — never both, and the grid is the same
 * `photoGroupItems` lane `privacy` draws, keyed by the `MediaStore` `_id` with a `contentType`.
 */
@Composable
internal fun PhotoCompressorScreen(
    state: PhotoCompressorState,
    onIntent: (PhotoCompressorIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted once, above the lane: a lambda written inside `items {}` is a new instance on every
    // recomposition and defeats the skip for the whole grid (`LLM.md` §8).
    val onTogglePhoto: (PhotoId) -> Unit = { id -> onIntent(PhotoCompressorIntent.PhotoToggled(id)) }
    val onToggleMonth: (String) -> Unit = { key -> onIntent(PhotoCompressorIntent.MonthToggled(key)) }
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.photo_compressor_title),
                onBack = { onIntent(PhotoCompressorIntent.BackPressed) },
            )
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = { onIntent(PhotoCompressorIntent.StartPressed) },
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )
            }
            if (state.introVisible) {
                CompressorIntroPanel(
                    estimate = state.estimate,
                    onStart = { onIntent(PhotoCompressorIntent.StartPressed) },
                )
                return@Column
            }
            when (state.phase) {
                ToolPhase.Scanning -> PhotoScanPanel(
                    label = stringResource(R.string.photo_scanning),
                    // The candidate query is one `MediaStore` round trip, so there is no honest
                    // per-photo count to show: an indeterminate bar is what `total = 0` renders.
                    done = 0,
                    total = 0,
                )

                ToolPhase.Completing -> PhotoCompletionPanel(
                    onFinished = { onIntent(PhotoCompressorIntent.CompletionAnimationFinished) },
                )

                else -> Unit
            }
            state.estimate?.let { estimate ->
                CompressionEstimateRow(
                    estimate = estimate,
                    modifier = Modifier.padding(horizontal = ScreenGutter, vertical = Spacing.sm),
                )
            }
            if (state.showEmptyState) {
                EmptyState(message = stringResource(R.string.photo_compressor_empty))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PHOTO_GRID_COLUMNS),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = ScreenGutter,
                        end = ScreenGutter,
                        bottom = PageSpacing.listBottom,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    photoGroupItems(
                        groups = state.groups,
                        selectedIds = state.selectedIds,
                        onToggleGroup = onToggleMonth,
                        onTogglePhoto = onTogglePhoto,
                    )
                }
            }
            SelectionBar(
                selectedCount = state.selectedCount,
                selectedBytes = state.selectedBytes,
                onSelectAll = { onIntent(PhotoCompressorIntent.SelectAllToggled) },
                // Disabled, not a silent no-op: `canContinue` is what gates the hand-off (§3.5).
                onDelete = { if (state.canContinue) onIntent(PhotoCompressorIntent.ContinuePressed) },
                actionLabel = stringResource(R.string.photo_action_continue),
            )
        }
    }
}
