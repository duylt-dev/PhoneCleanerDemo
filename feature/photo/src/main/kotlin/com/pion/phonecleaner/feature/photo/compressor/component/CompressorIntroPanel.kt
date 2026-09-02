package com.pion.phonecleaner.feature.photo.compressor.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.photo.CompressionEstimate
import com.pion.phonecleaner.feature.photo.R

/**
 * The landing panel — `docs/screens/13-photo-and-media.md` §3.3.
 *
 * It carries **no figure of its own**. The competitor's panel states "Before 807KB / After
 * 484KB(-40%)" and "up to about 40%" as literal strings its own encoder never produces (§3.5); here
 * the row appears only once something has actually been measured, and it is formatted through
 * `rememberByteFormat()` so it is a real locale-aware size rather than a baked one.
 */
@Composable
internal fun CompressorIntroPanel(
    estimate: CompressionEstimate?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.photo_compressor_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        estimate?.let { CompressionEstimateRow(it, Modifier.fillMaxWidth()) }
        Button(onClick = onStart) { Text(stringResource(R.string.photo_compressor_start)) }
    }
}

/**
 * "Now → After", both measured, plus how many photos the figure came from.
 *
 * **No percentage.** A percentage is a performance claim, and the one the competitor prints
 * disagrees with its own engine (`LLM.md` §1 wording ban; §3.5).
 */
@Composable
internal fun CompressionEstimateRow(
    estimate: CompressionEstimate,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabelledSize(stringResource(R.string.photo_compressor_estimate_before), bytes.size(estimate.beforeBytes).toString())
            LabelledSize(stringResource(R.string.photo_compressor_estimate_after), bytes.size(estimate.afterBytes).toString())
        }
        Text(
            text = pluralStringResource(
                R.plurals.photo_compressor_estimate_sample,
                estimate.sampledCount,
                estimate.sampledCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LabelledSize(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}
