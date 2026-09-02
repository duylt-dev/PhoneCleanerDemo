package com.pion.phonecleaner.feature.home.component

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.feature.home.R

/**
 * Storage occupancy, drawn as an arc.
 *
 * `animateIntAsState` no-ops when the target is unchanged. The competitor replays a 500 ms
 * `ObjectAnimator` sweep on **every** resume, so returning from any feature restarts the animation
 * (`docs/screens/11-home.md` §1.3).
 *
 * This is a measurement of how full the volume is — the one percentage this app may show. No
 * performance figure is presented anywhere (`LLM.md` §1, `docs/screens/11-home.md` §4.2).
 */
@Composable
internal fun StorageRing(
    usedPercent: Int,
    modifier: Modifier = Modifier,
) {
    val animated by animateIntAsState(
        targetValue = usedPercent.coerceIn(0, 100),
        animationSpec = tween(durationMillis = SweepMillis),
        label = "storageRingSweep",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.home_storage_ring_description, animated)

    Box(
        modifier = modifier
            .size(RingDiameter)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(RingDiameter)) {
            val stroke = RingStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, START_ANGLE, FULL_SWEEP, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(
                color = fill,
                startAngle = START_ANGLE,
                sweepAngle = FULL_SWEEP * animated / 100f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text(
            text = stringResource(R.string.home_storage_ring_value, animated),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** POSITIONS, not gaps — measured against the hero card, so they are not on the 4 dp scale (MVI §11). */
private val RingDiameter = 128.dp
private val RingStroke = 12.dp

private const val START_ANGLE = -90f
private const val FULL_SWEEP = 360f
private const val SweepMillis = 500
