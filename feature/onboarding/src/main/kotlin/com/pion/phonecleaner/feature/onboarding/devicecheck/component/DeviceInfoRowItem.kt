package com.pion.phonecleaner.feature.onboarding.devicecheck.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoRow
import com.pion.phonecleaner.domain.model.onboarding.StepStatus
import com.pion.phonecleaner.feature.onboarding.R

/**
 * One `comgeria.xml` row: a timeline rail, a status mark, a title and its reading.
 *
 * The row is stateless and derives nothing — `row.status` decides what is drawn, and the spinner is
 * **composed only while that status is `Loading`**, so nothing animates once the row is done. The
 * competitor's ViewHolder starts an `ObjectAnimator` with `repeatCount = -1` and relies on the view
 * being recycled to stop it (`AssimssesActivity:303-308`).
 */
@Composable
internal fun DeviceInfoRowItem(
    row: DeviceInfoRow,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(RowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        TimelineMark(status = row.status, isFirst = isFirst, isLast = isLast)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = stringResource(row.field.labelRes()),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = row.value.render(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** `eligrie` above and `apaceozone` below: the first row hides the top rail, the last the bottom. */
@Composable
private fun TimelineMark(
    status: StepStatus,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(RailColumnWidth).height(RowHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Rail(visible = !isFirst, modifier = Modifier.weight(1f))
        Box(Modifier.size(MarkSize), contentAlignment = Alignment.Center) {
            when (status) {
                StepStatus.Idle -> Unit
                StepStatus.Loading -> LoadingMark()
                StepStatus.Done -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.onboarding_device_row_done),
                    modifier = Modifier.size(TickSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Rail(visible = !isLast, modifier = Modifier.weight(1f))
    }
}

/**
 * `cofira` — a 900 ms linear rotation, `repeatCount = -1`. `RepeatMode.Restart` so the mark turns
 * one way; `Reverse` would rock back and forth, which the competitor's `ObjectAnimator` does not.
 */
@Composable
private fun LoadingMark(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "device-check-row-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SpinnerMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "device-check-row-angle",
    )
    Icon(
        imageVector = Icons.Rounded.Refresh,
        contentDescription = stringResource(R.string.onboarding_device_row_loading),
        modifier = modifier.size(SpinnerSize).rotate(angle),
        tint = MaterialTheme.colorScheme.primary,
    )
}

/**
 * A decoration, so it is removed from the accessibility tree: a screen reader announcing two
 * unlabelled boxes per row is noise on top of a row that already reads its own title and value.
 */
@Composable
private fun Rail(visible: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(RailWidth)
            .clearAndSetSemantics { contentDescription = "" }
            .background(
                if (visible) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.surface,
            ),
    )
}

private val RowHeight = 72.dp
private val RailColumnWidth = 40.dp
private val MarkSize = 32.dp
private val TickSize = 18.dp
private val SpinnerSize = 20.dp
private val RailWidth = 1.dp

/** `ObjectAnimator.ofFloat(ROTATION, 0f, 360f)`, duration 900 ms (`AssimssesActivity:303-308`). */
private const val SpinnerMillis = 900
