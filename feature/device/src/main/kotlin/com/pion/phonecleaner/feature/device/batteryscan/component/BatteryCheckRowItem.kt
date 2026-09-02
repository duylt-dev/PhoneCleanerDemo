package com.pion.phonecleaner.feature.device.batteryscan.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.device.ScanStep
import com.pion.phonecleaner.domain.model.device.StepState
import com.pion.phonecleaner.feature.device.component.batteryCheckLabel

/**
 * One checklist row (`docs/screens/18-device-battery-and-apps.md` §4.3).
 *
 * Every parameter is stable, so five of the six rows skip on every tick.
 *
 * Two competitor mechanisms disappear rather than being ported:
 *
 * * The rail is drawn with a `Canvas`, and [isFirst]/[isLast] drop the segment that would run off the
 *   end. The competitor keeps an upper and a lower connector `View` on every row and sets the
 *   unwanted one `INVISIBLE` — two views per row that exist only to be hidden.
 * * The spinner is composed **only while `Running`**, so it stops itself. The competitor's is an
 *   `ObjectAnimator` on `ROTATION` that has to be cancelled and nulled on every rebind.
 */
@Composable
internal fun BatteryCheckRowItem(
    row: ScanStep,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val railColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = modifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(RailWidth).height(RowHeight),
            contentAlignment = Alignment.Center,
        ) {
            Rail(isFirst = isFirst, isLast = isLast, color = railColor)
            StepGlyph(row.state)
        }
        Text(
            text = batteryCheckLabel(row.id),
            modifier = Modifier.weight(1f).padding(start = Spacing.md),
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.state == StepState.Idle) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun Rail(isFirst: Boolean, isLast: Boolean, color: Color) {
    Canvas(Modifier.width(RailWidth).height(RowHeight)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val stroke = RailStroke.toPx()
        if (!isFirst) {
            drawLine(color, Offset(centerX, 0f), Offset(centerX, centerY), stroke)
        }
        if (!isLast) {
            drawLine(color, Offset(centerX, centerY), Offset(centerX, size.height), stroke)
        }
    }
}

@Composable
private fun StepGlyph(state: StepState) {
    when (state) {
        StepState.Idle -> Box(Modifier.size(GlyphSize))

        StepState.Running -> {
            val transition = rememberInfiniteTransition(label = "step-spinner")
            val angle by transition.animateFloat(
                initialValue = 0f,
                targetValue = FULL_TURN,
                // 900 ms, linear, infinite — the competitor's own ObjectAnimator spec.
                animationSpec = infiniteRepeatable(tween(SPIN_MILLIS, easing = LinearEasing)),
                label = "step-angle",
            )
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(GlyphSize).rotate(angle),
                tint = MaterialTheme.colorScheme.primary,
            )
        }

        StepState.Done -> AnimatedVisibility(visible = true) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null, // the row's label is the accessible name
                modifier = Modifier.size(GlyphSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val SPIN_MILLIS = 900
private const val FULL_TURN = 360f

/** Positions, not gaps (MVI §11): the rail column and its glyph, sized to the 24 dp icon grid. */
private val RowHeight = 48.dp
private val RailWidth = 32.dp
private val GlyphSize = 24.dp
private val RailStroke = 2.dp
