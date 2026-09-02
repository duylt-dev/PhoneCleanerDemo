package com.pion.phonecleaner.feature.network.traffic.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.network.R

/**
 * Mobile and Wi-Fi totals for the selected period.
 *
 * These are the sums over **every** UID the query returned, which is what `TrafficReport` guarantees.
 * The competitor sums its already-filtered rows, so its header answers a different question from its
 * body (`docs/reverse-engineering/19-network-and-speed-test.md` :506). The caption says which
 * question is being answered rather than leaving the reader to assume.
 *
 * The value and its unit are styled separately, which is what `FormattedSize` exists for.
 */
@Composable
internal fun TrafficTotalsCard(
    mobileBytes: Long,
    wifiBytes: Long,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(horizontal = ScreenGutter)) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                TotalColumn(stringResource(R.string.traffic_total_mobile), mobileBytes, Modifier.weight(1f))
                TotalColumn(stringResource(R.string.traffic_total_wifi), wifiBytes, Modifier.weight(1f))
            }
            Text(
                text = stringResource(R.string.traffic_totals_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TotalColumn(label: String, bytes: Long, modifier: Modifier = Modifier) {
    val formatted = rememberByteFormat().size(bytes)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(formatted.value, style = MaterialTheme.typography.headlineSmall)
            Text(formatted.unit, style = MaterialTheme.typography.labelLarge)
        }
    }
}
