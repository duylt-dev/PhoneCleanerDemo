package com.pion.phonecleaner.feature.device.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.device.R

/**
 * The counter both percentage scans draw (`docs/screens/18-device-battery-and-apps.md` §2.3, §6.1):
 * a ring, a large number, a `%` and a caption.
 *
 * One composable for both screens, against the competitor's two copies of the same widget animated
 * by two different interpolators — the drift §6.4 records.
 *
 * **The number is a progress percentage and nothing else.** It is not a score, not a health figure
 * and not an improvement: the wording ban (`LLM.md` §1) is at its sharpest in this cluster, and the
 * caption is supplied by the caller precisely so each screen names what it is reading.
 *
 * ## UNKNOWN — the Lottie composition
 *
 * §2.3 and §6.1 both call for a `LottieAnimation`. `lottie-compose` is declared in
 * `gradle/libs.versions.toml` but is on no module's dependency list, and no `.json` composition
 * exists — looked for under every module's `src/main/res/raw` and `src/main/assets`. The ring below
 * is the Compose-native stand-in the junk cluster already established
 * (`feature/junk/component/JunkPhaseAnimation.kt`); the composition drops in behind these same two
 * parameters on the day the asset does.
 */
@Composable
internal fun ScanProgressPanel(
    progress: Int,
    caption: String,
    modifier: Modifier = Modifier,
) {
    // The ring is animated, the number is not: a counter that interpolates would show a percentage
    // the scan has not reached, and the ticker already moves at 60 Hz.
    val fraction by animateFloatAsState(
        targetValue = progress.coerceIn(0, 100) / 100f,
        animationSpec = tween(RING_SETTLE_MILLIS),
        label = "scan-progress-ring",
    )
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.size(RingSize),
                strokeWidth = RingStroke,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = progress.toString(),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    text = stringResource(R.string.device_percent_symbol),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val RING_SETTLE_MILLIS = 120

/** Positions, not gaps (MVI §11): the hero ring and its stroke. */
private val RingSize = 180.dp
private val RingStroke = 8.dp
