package com.pion.phonecleaner.feature.files.whatsapp.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.whatsapp.CleanProgress

/**
 * The live readout (§6.3). It animates toward `remainingBytes` with `animateFloatAsState` — no
 * `ValueAnimator`, and nothing reads a number back out of a `TextView`.
 *
 * The number **moves**, because it moves when bytes are actually freed. The competitor posts the
 * same value per file to an observer that discards the payload, so its readout never changes
 * (§6.5).
 */
@Composable
internal fun CleaningOverlay(progress: CleanProgress, modifier: Modifier = Modifier) {
    val bytes = rememberByteFormat()
    val fraction by animateFloatAsState(
        targetValue = if (progress.totalCount == 0) {
            0f
        } else {
            (progress.deletedCount + progress.failedCount).toFloat() / progress.totalCount
        },
        label = "whatsapp-clean",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
            .padding(horizontal = ScreenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = bytes.size(progress.remainingBytes).toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        LinearProgressIndicator(progress = { fraction })
        Text(
            text = stringResource(R.string.files_removing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
        )
        if (progress.failedCount > 0) {
            Text(
                text = stringResource(R.string.files_delete_failed, progress.failedCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        }
    }
}

private const val ScrimAlpha = 0.86f
