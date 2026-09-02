package com.pion.phonecleaner.feature.antivirus.scan.component

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.antivirus.R
import com.pion.phonecleaner.feature.antivirus.scan.AntivirusScanState

/**
 * The running screen: a bar, the app in flight, the elapsed time, the coverage note and the
 * attribution (`docs/screens/15-antivirus.md` §1.3).
 *
 * It takes the whole state and raises no intent — nothing on it is tappable. The stop affordance is
 * BACK, which the Route already owns.
 */
@Composable
internal fun ScanProgressPanel(
    state: AntivirusScanState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenGutter)
            .padding(top = PageSpacing.headerToContent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        if (state.isRunning) ScanProgressBar(state.progress)

        Text(
            text = when {
                !state.isRunning -> stringResource(R.string.antivirus_scan_idle)
                state.scanningLabel.isEmpty() -> stringResource(R.string.antivirus_scan_preparing)
                else -> state.scanningLabel
            },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (state.isRunning) {
            Text(
                // `formatElapsedTime` is the platform's own m:ss, so the separator follows the
                // locale instead of a format string invented here.
                text = stringResource(
                    R.string.antivirus_scan_elapsed,
                    DateUtils.formatElapsedTime(state.elapsedMs / MILLIS_PER_SECOND),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = stringResource(R.string.antivirus_scan_network_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // system-architecture.md §8.4: a scan that could not see everything says so, on the screen
        // that ran it. The competitor degrades silently and reports nothing.
        if (state.coverage.isPartial) {
            Text(
                text = stringResource(R.string.antivirus_coverage_partial),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Two modes, and no third. `null` is indeterminate — **the first half is not synthesised**: the
 * competitor animates 0 → 50 % over 300 000 ms and then maps the real counts onto 50 → 100 %, so
 * its bar is a fiction for up to five minutes and then jumps to half full (§1.5).
 *
 * The determinate value is animated so the bar slews between coarse per-app ticks instead of
 * stepping. That animation is local and visual, which MVI §4 permits.
 */
@Composable
private fun ScanProgressBar(progress: Float?, modifier: Modifier = Modifier) {
    if (progress == null) {
        LinearProgressIndicator(modifier.fillMaxWidth())
    } else {
        val animated by animateFloatAsState(targetValue = progress, label = "scan-progress")
        LinearProgressIndicator(progress = { animated }, modifier = modifier.fillMaxWidth())
    }
}

/** Named rather than inlined: `1_000` beside a division reads as a magic number at the call site. */
private const val MILLIS_PER_SECOND = 1_000L
