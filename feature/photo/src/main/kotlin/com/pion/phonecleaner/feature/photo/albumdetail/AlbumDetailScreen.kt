package com.pion.phonecleaner.feature.photo.albumdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.R as CoreUiR
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SelectionBar
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.component.PHOTO_CELL_CONTENT_TYPE
import com.pion.phonecleaner.feature.photo.component.PHOTO_GRID_COLUMNS
import com.pion.phonecleaner.feature.photo.component.PhotoCell
import com.pion.phonecleaner.feature.photo.component.PhotoScanPanel

/**
 * `docs/screens/13-photo-and-media.md` §7.3. The grid is the outer scrollable — there is no
 * self-measuring grid nested inside another scroller — and the header carries the album's name,
 * which the competitor never shows.
 */
@Composable
internal fun AlbumDetailScreen(
    state: AlbumDetailState,
    onIntent: (AlbumDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // ONE instance for the whole grid, hoisted above `items {}` (`LLM.md` §8).
    val onToggle: (PhotoId) -> Unit = { id -> onIntent(AlbumDetailIntent.PhotoToggled(id)) }
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = state.folderName,
                onBack = { onIntent(AlbumDetailIntent.BackPressed) },
            )
            AlbumDetailBanners(state, onIntent)
            if (state.showEmptyState) {
                EmptyState(message = stringResource(R.string.photo_album_empty))
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
                    items(
                        items = state.photos,
                        key = { it.id.value },
                        contentType = { PHOTO_CELL_CONTENT_TYPE },
                    ) { photo ->
                        PhotoCell(
                            photo = photo,
                            selected = photo.id in state.selectedIds,
                            onToggle = onToggle,
                        )
                    }
                }
            }
            SelectionBar(
                selectedCount = state.selectedCount,
                selectedBytes = state.selectedBytes,
                onSelectAll = { onIntent(AlbumDetailIntent.SelectAllToggled) },
                onDelete = { onIntent(AlbumDetailIntent.DeletePressed) },
            )
        }
    }
    if (state.isDeleteConfirmVisible) {
        AppDialog(
            onDismissRequest = { onIntent(AlbumDetailIntent.DeleteDismissed) },
            confirmLabel = stringResource(CoreUiR.string.action_delete),
            onConfirm = { onIntent(AlbumDetailIntent.DeleteConfirmed) },
            title = stringResource(R.string.photo_album_delete_title),
            body = pluralStringResource(
                R.plurals.photo_album_delete_body,
                state.selectedCount,
                state.selectedCount,
            ),
            dismissLabel = stringResource(CoreUiR.string.action_cancel),
        )
    }
}

/**
 * The three things this screen has to say and the competitor does not: the scan is running, the
 * system is asking, and the delete did not remove everything (§7.5).
 */
@Composable
private fun AlbumDetailBanners(state: AlbumDetailState, onIntent: (AlbumDetailIntent) -> Unit) {
    // Scanning and Deleting are the two phases with something to draw. There is no `Completing`
    // arm because this screen never enters it: a folder query is not a scan with a reveal.
    if (state.phase == ToolPhase.Scanning || state.phase == ToolPhase.Deleting) {
        PhotoScanPanel(label = stringResource(R.string.photo_scanning), done = 0, total = 0)
    }
    if (state.isAwaitingConsent) Notice(stringResource(R.string.photo_consent_pending))
    if (state.consentDeclined) Notice(stringResource(R.string.photo_consent_declined))
    if (state.failedCount > 0) {
        Notice(
            pluralStringResource(R.plurals.photo_delete_failed, state.failedCount, state.failedCount),
        )
    }
    state.error?.let { error ->
        ErrorCard(
            error = error,
            onRetry = { onIntent(AlbumDetailIntent.ScreenStarted) },
            modifier = Modifier.padding(horizontal = ScreenGutter),
        )
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
