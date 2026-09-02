package com.pion.phonecleaner.feature.photo.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.component.header.OverlayHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.photo.R

/**
 * `docs/screens/13-photo-and-media.md` §2.3.
 *
 * A `HorizontalPager`, replacing a `RecyclerView` whose item `layoutParams` are forced to
 * `match_parent` and whose snapping is a hand-rolled `smoothScrollToPosition` inside
 * `onScrollStateChanged`.
 *
 * `OverlayHeader`, not `PageHeader`: this screen draws over a photograph, so the header applies the
 * top inset itself (MVI §11).
 */
@Composable
internal fun PhotoPreviewScreen(
    state: PhotoPreviewState,
    onIntent: (PhotoPreviewIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        if (state.photos.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                OverlayHeader(
                    title = stringResource(R.string.photo_preview_title),
                    onBack = { onIntent(PhotoPreviewIntent.ClosePressed) },
                )
                EmptyState(message = stringResource(R.string.photo_session_lost))
            }
            return@Surface
        }
        val pagerState = rememberPagerState(
            initialPage = state.index,
            pageCount = { state.photos.size },
        )
        // The one place a page position crosses into the ViewModel. `settledPage`, not the live
        // offset, so a half-finished drag never rewrites the state.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { page -> onIntent(PhotoPreviewIntent.PageChanged(page)) }
        }
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = state.photos[page].contentUri,
                    contentDescription = state.photos[page].displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
            OverlayHeader(
                title = stringResource(R.string.photo_preview_title),
                modifier = Modifier.align(Alignment.TopCenter),
                onBack = { onIntent(PhotoPreviewIntent.ClosePressed) },
            )
            PreviewFooter(
                state = state,
                onIntent = onIntent,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PreviewFooter(
    state: PhotoPreviewState,
    onIntent: (PhotoPreviewIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(
                R.string.photo_preview_counter,
                state.counterPosition,
                state.counterTotal,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        if (state.isCurrentKept) {
            Text(
                text = stringResource(R.string.photo_keep_badge),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = { onIntent(PhotoPreviewIntent.SelectionToggled) }) {
            Text(
                stringResource(
                    if (state.isCurrentSelected) R.string.photo_preview_deselect
                    else R.string.photo_preview_select,
                ),
            )
        }
    }
}
