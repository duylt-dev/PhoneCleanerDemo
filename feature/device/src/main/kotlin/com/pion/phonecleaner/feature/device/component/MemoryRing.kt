package com.pion.phonecleaner.feature.device.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.device.MemoryInfo
import com.pion.phonecleaner.feature.device.R

/**
 * The device-memory ring (`docs/screens/18-device-battery-and-apps.md` §6.1, §6.2) — **one**
 * composable, drawn by both `runningappsscan` and `runningapps`.
 *
 * ### What this ring may and may not say
 *
 * It reports an **occupancy measurement with its unit**: how much of this device's RAM is in use
 * right now. It is labelled *Device memory*, because the list beside it is a list of apps and the
 * competitor's juxtaposition — a device-wide figure next to a per-app Stop button — implies a causal
 * link that no data here supports: the row model carries a package name and nothing else, so there is
 * no per-app figure anywhere in the cluster (§6.5).
 *
 * Three consequences, all of them the wording ban (`LLM.md` §1) applied to this one widget:
 *
 * 1. **It does not animate downward after a Stop.** A number that falls when the user taps something
 *    is a claim about what the tap did.
 * 2. **No string here interprets the figure** — not as speed, not as headroom, not as an amount that
 *    could be freed, and never as a percentage improvement.
 * 3. `null` is a **shimmer**, never a stale frame and never a zero: the competitor reads memory on
 *    the main thread in `onCreate` on both screens, so it has no null to render.
 */
@Composable
internal fun MemoryRing(
    memory: MemoryInfo?,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(contentAlignment = Alignment.Center) {
            MemoryArc(usedPercent = memory?.usedPercent, track = track, fill = fill)
            Text(
                text = if (memory == null) {
                    stringResource(R.string.value_unavailable)
                } else {
                    stringResource(
                        R.string.device_status_value_bytes_of,
                        bytes.size(memory.usedBytes).toString(),
                        bytes.size(memory.totalBytes).toString(),
                    )
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = stringResource(R.string.running_apps_memory_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The arc itself. The pulse is composed **only** while the reading is null, so it stops itself when
 * the value lands — nothing has to remember to cancel it, which is the difference from an
 * `ObjectAnimator` held on a field.
 */
@Composable
private fun MemoryArc(usedPercent: Int?, track: Color, fill: Color) {
    val transition = rememberInfiniteTransition(label = "memory-ring")
    val pulse by transition.animateFloat(
        initialValue = MIN_ALPHA,
        targetValue = MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_MILLIS),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "memory-ring-alpha",
    )
    val sweep = (usedPercent ?: 0).coerceIn(0, 100) / 100f * FULL_TURN
    Canvas(Modifier.size(RingSize)) {
        val stroke = Stroke(width = RingStroke.toPx())
        drawArc(
            color = track,
            startAngle = START_ANGLE,
            sweepAngle = FULL_TURN,
            useCenter = false,
            alpha = if (usedPercent == null) pulse else 1f,
            style = stroke,
        )
        if (usedPercent != null) {
            drawArc(
                color = fill,
                startAngle = START_ANGLE,
                sweepAngle = sweep,
                useCenter = false,
                style = stroke,
            )
        }
    }
}

private const val START_ANGLE = -90f
private const val FULL_TURN = 360f
private const val MIN_ALPHA = 0.25f
private const val MAX_ALPHA = 0.75f
private const val PULSE_MILLIS = 900

/** Positions, not gaps (MVI §11): the ring and its stroke. */
private val RingSize = 160.dp
private val RingStroke = 10.dp
