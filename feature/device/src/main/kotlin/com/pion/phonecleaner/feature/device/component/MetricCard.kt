package com.pion.phonecleaner.feature.device.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.device.R

/**
 * **One** card for all five device-status blocks
 * (`docs/screens/18-device-battery-and-apps.md` §3.3), against the competitor's five hand-written
 * `LinearLayout` blocks in a 240-line XML.
 *
 * [percent] drives the track only. It is an **occupancy** figure — how full something is right now —
 * and no string in this cluster interprets it: the wording ban (`LLM.md` §1) is at its sharpest on
 * exactly these cards, because the competitor's device and battery screens are where its performance
 * claims live. A card reports a measured value with its unit and stops there.
 *
 * [onAction] is nullable because two of the five cards — processor and display — have nothing to
 * navigate to. A `null` removes the button from the composition, which is what makes one
 * implementation serve five cards instead of one plus four `visibility="gone"` decoys.
 */
@Composable
internal fun MetricCard(
    icon: ImageVector,
    title: String,
    percent: Int?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // `title` is the accessible name
                    modifier = Modifier.size(CardIconSize),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    modifier = Modifier.weight(1f).padding(start = Spacing.md),
                    style = MaterialTheme.typography.titleMedium,
                )
                if (onAction != null) {
                    TextButton(onClick = onAction) {
                        Text(stringResource(R.string.device_status_check))
                    }
                }
            }
            ShimmerTrack(percent)
            content()
        }
    }
}

/** A **position**, not a gap (MVI §11): the 24 dp Material list-icon size. */
private val CardIconSize = 24.dp
