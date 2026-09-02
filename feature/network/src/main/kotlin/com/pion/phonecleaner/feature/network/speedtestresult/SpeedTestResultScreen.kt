package com.pion.phonecleaner.feature.network.speedtestresult

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.network.R

/**
 * Stateless (`docs/screens/19-network-and-speed-test.md` §3.3). A document screen, so `PageHeader`.
 *
 * Each tile renders `rememberByteFormat().rate(value)`, which returns a value and a unit separately
 * so the two can be styled apart — what the competitor's three single-string formatters make
 * impossible. The locale comes from the composition, never from a forced `Locale.US`.
 *
 * With no measurement the tiles are replaced by one sentence. A `0 B/s` tile would be a figure
 * nobody measured.
 */
@Composable
internal fun SpeedTestResultScreen(
    state: SpeedTestResultState,
    onIntent: (SpeedTestResultIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.speed_test_title),
                onBack = { onIntent(SpeedTestResultIntent.BackPressed) },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
            ) {
                if (state.isMeasured) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                        SpeedTile(
                            label = stringResource(R.string.speed_result_download),
                            bytesPerSecond = state.downloadBytesPerSecond,
                            modifier = Modifier.weight(1f),
                        )
                        SpeedTile(
                            label = stringResource(R.string.speed_result_upload),
                            bytesPerSecond = state.uploadBytesPerSecond,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.speed_result_not_measured),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OutlinedButton(
                    onClick = { onIntent(SpeedTestResultIntent.RunAgainPressed) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.speed_result_run_again))
                }
                Button(
                    onClick = { onIntent(SpeedTestResultIntent.DonePressed) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.speed_result_done))
                }
            }
        }
    }
}

@Composable
private fun SpeedTile(label: String, bytesPerSecond: Long, modifier: Modifier = Modifier) {
    val rate = rememberByteFormat().rate(bytesPerSecond)
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(rate.value, style = MaterialTheme.typography.headlineMedium)
                Text(rate.unit, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
