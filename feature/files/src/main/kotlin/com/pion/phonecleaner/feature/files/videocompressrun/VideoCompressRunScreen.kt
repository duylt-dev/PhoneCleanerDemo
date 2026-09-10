package com.pion.phonecleaner.feature.files.videocompressrun

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.R as CoreUiR
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.videocompressrun.component.VideoCompressRunDialogs
import com.pion.phonecleaner.feature.files.videocompressrun.component.VideoCompressRunOverlay

/**
 * `videocompressrun` — `plans/260907-0142-video-compression/phase-07-run-screen.md` step 4.
 *
 * A plain `LazyColumn` of read-only rows: **no pager** (a video thumbnail pager buys nothing and
 * costs a decoder per page), and no checkbox — nothing on this screen is selectable, the run already
 * covers every row the picker sent.
 */
@Composable
internal fun VideoCompressRunScreen(
    state: VideoCompressRunState,
    onIntent: (VideoCompressRunIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.video_compress_run_title),
                onBack = { onIntent(VideoCompressRunIntent.BackPressed) },
            )
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = { onIntent(VideoCompressRunIntent.ScreenStarted) },
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )
            }
            if (state.sessionLost) {
                EmptyState(message = stringResource(CoreUiR.string.error_not_found))
            } else {
                VideoCompressRunList(state.videos, Modifier.weight(1f))
                state.run?.let { progress ->
                    VideoCompressRunOverlay(
                        progress = progress,
                        timeoutSeconds = state.timeoutSeconds,
                        onFinished = { onIntent(VideoCompressRunIntent.CompletionAnimationFinished) },
                    )
                }
                state.spaceShortfall?.let { VideoCompressRunSpaceShortfall(it) }
                if (state.isFinished) state.run?.let { VideoCompressRunResultPanel(state, it) }
                VideoCompressRunPrimaryAction(state, onIntent)
            }
        }
    }
    VideoCompressRunDialogs(state, onIntent)
}

@Composable
private fun VideoCompressRunList(videos: List<VideoCandidate>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = ScreenGutter, vertical = Spacing.sm),
    ) {
        items(items = videos, key = { it.id }) { candidate -> VideoCompressRunRow(candidate) }
    }
}

@Composable
private fun VideoCompressRunRow(candidate: VideoCandidate, modifier: Modifier = Modifier) {
    val bytes = rememberByteFormat()
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(RowIconSize))
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                text = candidate.file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = bytes.size(candidate.sizeBytes).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VideoCompressRunSpaceShortfall(deficitBytes: Long, modifier: Modifier = Modifier) {
    val bytes = rememberByteFormat()
    Text(
        text = stringResource(R.string.video_compress_no_space, bytes.size(deficitBytes).toString()),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier.padding(horizontal = ScreenGutter, vertical = Spacing.sm),
    )
}

/**
 * `producedBytes`/`showOriginalsKeptNotice` — never a figure the run did not measure (key insight 1).
 */
@Composable
private fun VideoCompressRunResultPanel(
    state: VideoCompressRunState,
    run: VideoRunProgress,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (run.done > 0) {
            Text(
                pluralStringResource(
                    R.plurals.video_compress_result_created,
                    run.done,
                    run.done,
                    bytes.size(state.producedBytes).toString(),
                ),
            )
        }
        if (run.skipped > 0) {
            Text(pluralStringResource(R.plurals.video_compress_result_skipped, run.skipped, run.skipped))
        }
        if (run.failed > 0) {
            Text(pluralStringResource(R.plurals.video_compress_result_failed, run.failed, run.failed))
        }
        if (state.showOriginalsKeptNotice) Text(stringResource(R.string.video_compress_result_kept))
    }
}

/** One of three, decided by state, never by a flag: compress, delete, or nothing left to offer. */
@Composable
private fun VideoCompressRunPrimaryAction(
    state: VideoCompressRunState,
    onIntent: (VideoCompressRunIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    val buttonModifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.lg)
    when {
        state.canCompress -> Button(
            onClick = { onIntent(VideoCompressRunIntent.CompressAllPressed) },
            modifier = buttonModifier,
        ) { Text(stringResource(R.string.video_compress_run_action)) }

        state.canDeleteOriginals -> Button(
            onClick = { onIntent(VideoCompressRunIntent.DeleteOriginalsPressed) },
            modifier = buttonModifier,
        ) {
            Text(
                stringResource(
                    R.string.video_compress_delete_originals,
                    bytes.size(state.deletableBytes).toString(),
                ),
            )
        }

        else -> Unit
    }
}

/** A **position**, not a gap (MVI §11): the leading glyph of a two-line list row. */
private val RowIconSize = 40.dp
