package com.pion.phonecleaner.feature.network.speedtest.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.network.SpeedTestStage
import com.pion.phonecleaner.feature.network.R

/**
 * The measurement's position, as an arc (`docs/screens/19-network-and-speed-test.md` §2.3 D-1).
 *
 * It replaces a Lottie asset driven by a `ValueAnimator` held on an Activity field, which means the
 * number on screen and the number in state are now the same number: this `Canvas` reads
 * [progressPercent] and nothing else.
 *
 * The competitor's Lottie carries no `contentDescription` and announces nothing. This one is one
 * semantics node describing the stage, the position and — only when a rate has actually been
 * measured — the rate. When [bytesPerSecond] is `null` the description simply omits it; it never
 * substitutes a zero.
 */
@Composable
internal fun SpeedDial(
    progressPercent: Int,
    stage: SpeedTestStage,
    bytesPerSecond: Long?,
    modifier: Modifier = Modifier,
) {
    val stageLabel = stringResource(stage.labelRes())
    val rate = bytesPerSecond?.let { rememberByteFormat().rate(it) }
    val description = if (rate == null) {
        stringResource(R.string.speed_test_dial_description, stageLabel, progressPercent)
    } else {
        stringResource(
            R.string.speed_test_dial_description_rate,
            stageLabel,
            progressPercent,
            rate.toString(),
        )
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val indicator = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter)
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.fillMaxWidth(DialWidthFraction), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val stroke = Stroke(width = DialStrokeDp.toPx())
                val inset = stroke.width / 2f
                val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(
                    color = track,
                    startAngle = SweepStart,
                    sweepAngle = SweepTotal,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
                drawArc(
                    color = indicator,
                    startAngle = SweepStart,
                    sweepAngle = SweepTotal * (progressPercent.coerceIn(0, 100) / 100f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = stroke,
                )
            }
            RateReadout(rate?.value, rate?.unit, stageLabel)
        }
    }
}

/** No rate yet means no readout. There is no "0 B/s" placeholder anywhere in this cluster. */
@Composable
private fun RateReadout(value: String?, unit: String?, stageLabel: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (value != null && unit != null) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(value, style = MaterialTheme.typography.displaySmall)
                Text(unit, style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(
            text = stageLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun SpeedTestStage.labelRes(): Int = when (this) {
    SpeedTestStage.Download -> R.string.speed_test_stage_download
    SpeedTestStage.Upload -> R.string.speed_test_stage_upload
}

private const val DialWidthFraction = 0.8f
private const val SweepStart = 135f
private const val SweepTotal = 270f
private val DialStrokeDp = 14.dp
