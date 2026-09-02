package com.pion.phonecleaner.feature.photo.compressrun.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.component.PhotoCompletionPanel
import com.pion.phonecleaner.feature.photo.compressrun.CompressProgress

/**
 * The run, drawn — `docs/screens/13-photo-and-media.md` §4.3.
 *
 * A **determinate** bar driven by `done / total`, both of which the engine counts. There is no
 * 4 000 ms floor and no timer: the competitor pads every run with `delay(od.q0.a(t0))` so a
 * forty-second run and a two-second run look the same (§0.5).
 *
 * When the run is finished the completion panel takes over and reports back with an Intent, which is
 * what raises the navigation hop — never an ad SDK's close callback (§0.2).
 */
@Composable
internal fun CompressRunOverlay(
    progress: CompressProgress,
    currentUri: String?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentUri != null) {
                AsyncImage(
                    model = currentUri,
                    contentDescription = null,
                    modifier = Modifier.size(CurrentThumbnailSize).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(
                        R.string.photo_scan_progress,
                        progress.done + progress.failedCount,
                        progress.total,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    // The measured figure, formatted for the locale — never `0.6 × selectedBytes`.
                    text = stringResource(
                        R.string.photo_compress_saved,
                        bytes.size(progress.savedBytes).toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinearProgressIndicator(
            progress = {
                if (progress.total == 0) 0f
                else (progress.done + progress.failedCount).toFloat() / progress.total
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (progress.failedCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.photo_failed_count,
                    progress.failedCount,
                    progress.failedCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (progress.isFinished) PhotoCompletionPanel(onFinished = onFinished)
    }
}

private val CurrentThumbnailSize = 48.dp
