package com.pion.phonecleaner.feature.photo.similar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
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
import com.pion.phonecleaner.feature.photo.similar.component.SimilarPhotosDialogs
import com.pion.phonecleaner.feature.photo.similar.component.SimilarPhotosNotices

/**
 * `docs/screens/13-photo-and-media.md` §1.3.
 *
 * Every callback is hoisted **once**, above the lane: a lambda written inside `items {}` is a new
 * instance on every recomposition and defeats the skip for every cell in the grid (`LLM.md` §8).
 * With `Photo` and `PhotoGroup` `@Immutable` and the selection an `ImmutableSet<PhotoId>` off the
 * model, a tap recomposes one cell.
 */
@Composable
internal fun SimilarPhotosScreen(
    state: SimilarPhotosState,
    onIntent: (SimilarPhotosIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onTogglePhoto: (PhotoId) -> Unit = { id -> onIntent(SimilarPhotosIntent.PhotoToggled(id)) }
    val onToggleGroup: (String) -> Unit = { key -> onIntent(SimilarPhotosIntent.GroupCleanupPressed(key)) }
    val onOpenPhoto: (PhotoId) -> Unit = { id -> onIntent(SimilarPhotosIntent.PhotoOpened(id)) }
    // Resolved above the lane: `stringResource` is a @Composable call and `LazyGridScope` is not one.
    val groupActionLabel = stringResource(R.string.photo_similar_group_cleanup)
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.photo_similar_title),
                onBack = { onIntent(SimilarPhotosIntent.BackPressed) },
            )
            TotalsHeader(bytes = state.totalBytes)
            when (state.phase) {
                ToolPhase.Scanning -> PhotoScanPanel(
                    label = stringResource(R.string.photo_similar_hashing),
                    done = state.hashed,
                    total = state.toHash,
                )

                ToolPhase.Completing -> PhotoCompletionPanel(
                    onFinished = { onIntent(SimilarPhotosIntent.CompletionAnimationFinished) },
                )

                else -> Unit
            }
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = { onIntent(SimilarPhotosIntent.ScreenStarted) },
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )
            }
            SimilarPhotosNotices(state)
            if (state.showEmptyState) {
                EmptyState(message = stringResource(R.string.photo_similar_empty))
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
                        onToggleGroup = onToggleGroup,
                        onTogglePhoto = onTogglePhoto,
                        // The kept photo is badged rather than hidden, so "keep the newest" is
                        // visible and the user can move it by hand (§1.4).
                        markKeptPhoto = true,
                        headerActionLabel = groupActionLabel,
                        onHeaderAction = onToggleGroup,
                        onOpenPhoto = onOpenPhoto,
                    )
                }
            }
            SelectionBar(
                selectedCount = state.selectedCount,
                selectedBytes = state.selectedBytes,
                onSelectAll = { onIntent(SimilarPhotosIntent.SelectAllToggled) },
                onDelete = { if (state.canDelete) onIntent(SimilarPhotosIntent.DeletePressed) },
            )
        }
    }
    SimilarPhotosDialogs(state, onIntent)
}

/** What the whole result weighs — formatted for the locale, never a hand-built string. */
@Composable
private fun TotalsHeader(bytes: Long) {
    val format = rememberByteFormat()
    Text(
        text = format.size(bytes).toString(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        style = MaterialTheme.typography.headlineSmall,
    )
}
