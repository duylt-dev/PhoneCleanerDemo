package com.pion.phonecleaner.feature.photo.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.component.PhotoCompletionPanel
import com.pion.phonecleaner.feature.photo.component.PhotoScanPanel

/**
 * `docs/screens/13-photo-and-media.md` §6.3. Keyed by `folderName`, which is the **full parent
 * path**: the competitor keys on the last segment, so `DCIM/Camera` and `Pictures/Camera` merge into
 * one row (§6.5).
 */
@Composable
internal fun AlbumsScreen(
    state: AlbumsState,
    onIntent: (AlbumsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One instance for the whole lane, hoisted above `items {}` (`LLM.md` §8).
    val onOpen: (String) -> Unit = { folder -> onIntent(AlbumsIntent.AlbumOpened(folder)) }
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(title = stringResource(R.string.photo_albums_title), onBack = { onIntent(AlbumsIntent.BackPressed) })
            when (state.phase) {
                ToolPhase.Scanning -> PhotoScanPanel(
                    label = stringResource(R.string.photo_scanning),
                    done = 0,
                    total = 0,
                )

                ToolPhase.Completing -> PhotoCompletionPanel(
                    onFinished = { onIntent(AlbumsIntent.CompletionAnimationFinished) },
                )

                else -> Unit
            }
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = { onIntent(AlbumsIntent.ScreenStarted) },
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )
            }
            if (state.showEmptyState) {
                EmptyState(message = stringResource(R.string.photo_albums_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = ScreenGutter,
                        end = ScreenGutter,
                        bottom = PageSpacing.listBottom,
                    ),
                ) {
                    items(
                        items = state.albums,
                        key = { it.folderName },
                        contentType = { AlbumRowContentType },
                    ) { album ->
                        AlbumRow(album = album, onOpen = onOpen)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumRow(album: PhotoAlbum, onOpen: (String) -> Unit) {
    val bytes = rememberByteFormat()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(album.folderName) }
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AsyncImage(
            model = album.coverUri,
            contentDescription = null, // the label below names the album
            modifier = Modifier.size(AlbumCoverSize).clip(MaterialTheme.shapes.small),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = album.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.photo_album_count, album.count, album.count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = bytes.size(album.totalBytes).toString(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * The cover square. A *position*, not a gap, so it is a named value rather than a `Spacing` step
 * (MVI §11): it is measured against the two-line label beside it.
 */
private val AlbumCoverSize = 56.dp

private const val AlbumRowContentType = "photoAlbumRow"
