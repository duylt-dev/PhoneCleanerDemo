package com.pion.phonecleaner.feature.files.videocompressrun.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.videocompressrun.VideoRunProgress

/**
 * The run, drawn — `plans/260907-0142-video-compression/phase-07-run-screen.md` step 5.
 *
 * A **determinate** outer bar driven by `settled / total`, and an inner one for the current video
 * driven by [VideoRunProgress.currentPercent]. `null` draws an indeterminate indicator and
 * [R.string.video_compress_run_preparing] — **never a 0 %**, because a zero the engine did not
 * produce reads as a measurement.
 *
 * No time floor, no `delay`: a run that takes forty seconds looks like forty seconds. The
 * competitor pads every run so a forty-second job and a two-second job look identical.
 *
 * When [VideoRunProgress.isFinished], [onFinished] fires immediately — there is no separate
 * completion panel to animate here (unlike the photo screen), because this screen still has a
 * delete step ahead of it and nothing to celebrate yet.
 */
@Composable
internal fun VideoCompressRunOverlay(
    progress: VideoRunProgress,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        LinearProgressIndicator(
            progress = {
                if (progress.total == 0) 0f else progress.settled.toFloat() / progress.total
            },
            modifier = Modifier.fillMaxWidth(),
        )
        val percent = progress.currentPercent
        if (percent == null) {
            Text(
                text = stringResource(R.string.video_compress_run_preparing),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            Text(
                text = stringResource(
                    R.string.video_compress_run_progress,
                    progress.currentIndex,
                    progress.total,
                    percent,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (progress.isFinished) LaunchedEffect(Unit) { onFinished() }
}
