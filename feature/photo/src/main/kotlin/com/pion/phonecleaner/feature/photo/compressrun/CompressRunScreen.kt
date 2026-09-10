package com.pion.phonecleaner.feature.photo.compressrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.compressrun.component.CompressRunDialogs
import com.pion.phonecleaner.feature.photo.compressrun.component.CompressRunOverlay

/**
 * `docs/screens/13-photo-and-media.md` §4.3.
 *
 * A `HorizontalPager` of square photo cards. **No page cache exists** — the competitor memoises one
 * full-size `ImageView` per visited page in a map nothing ever clears; Coil owns the caching here
 * and the pager owns the recycling.
 */
@Composable
internal fun CompressRunScreen(
    state: CompressRunState,
    onIntent: (CompressRunIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.photo_compress_run_title),
                onBack = { onIntent(CompressRunIntent.BackPressed) },
            )
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = { onIntent(CompressRunIntent.ScreenStarted) },
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )
            }
            if (state.photos.isEmpty()) {
                EmptyState(message = stringResource(R.string.photo_session_lost))
                return@Column
            }
            CompressRunPager(state = state, onIntent = onIntent, modifier = Modifier.weight(1f))
            state.run?.let { progress ->
                CompressRunOverlay(
                    progress = progress,
                    // The thumbnail of the photo the engine is on — `run.currentId`, never `page`.
                    currentUri = state.photos.firstOrNull { it.id == progress.currentId }?.contentUri,
                    onFinished = { onIntent(CompressRunIntent.CompletionAnimationFinished) },
                )
            }
            if (state.canCompress) {
                Button(
                    onClick = { onIntent(CompressRunIntent.CompressAllPressed) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenGutter, vertical = Spacing.lg),
                ) {
                    Text(stringResource(R.string.photo_compress_run_action))
                }
            }
        }
    }
    CompressRunDialogs(state, onIntent)
}

@Composable
private fun CompressRunPager(
    state: CompressRunState,
    onIntent: (CompressRunIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = state.page,
        pageCount = { state.photos.size },
    )
    // The one place a page position crosses into the ViewModel. `settledPage`, not the live offset,
    // so a half-finished drag never rewrites the state — and the compression index never moves it.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { page -> onIntent(CompressRunIntent.PageChanged(page)) }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = ScreenGutter),
            pageSpacing = Spacing.sm,
        ) { page ->
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                AsyncImage(
                    model = state.photos[page].contentUri,
                    contentDescription = state.photos[page].displayName,
                    modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text(
            text = stringResource(
                R.string.photo_preview_counter,
                state.counterPosition,
                state.counterTotal,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
