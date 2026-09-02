package com.pion.phonecleaner.feature.device.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

/**
 * A progress track that says "not read yet" instead of "zero".
 *
 * `docs/screens/18-device-battery-and-apps.md` §3.3: *"`percent == null` renders the track at zero
 * with a shimmer; there is no invented `25 %`."* An empty track and a track that failed to read look
 * identical, which is exactly the confusion the competitor's substituted constants create — so the
 * unread state pulses and the read state does not.
 *
 * The pulse is composed **only** while the value is null, so it stops itself when the reading lands.
 * Nothing has to remember to cancel it, which is the difference from an `ObjectAnimator` held on a
 * field.
 */
@Composable
internal fun ShimmerTrack(
    percent: Int?,
    modifier: Modifier = Modifier,
) {
    if (percent == null) {
        val transition = rememberInfiniteTransition(label = "unread-track")
        val alpha by transition.animateFloat(
            initialValue = MIN_ALPHA,
            targetValue = MAX_ALPHA,
            animationSpec = infiniteRepeatable(
                animation = tween(PULSE_MILLIS),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "unread-alpha",
        )
        LinearProgressIndicator(
            progress = { 0f },
            modifier = modifier.fillMaxWidth().alpha(alpha),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LinearProgressIndicator(
            progress = { percent.coerceIn(0, 100) / 100f },
            modifier = modifier.fillMaxWidth(),
        )
    }
}

private const val MIN_ALPHA = 0.25f
private const val MAX_ALPHA = 0.75f
private const val PULSE_MILLIS = 900
