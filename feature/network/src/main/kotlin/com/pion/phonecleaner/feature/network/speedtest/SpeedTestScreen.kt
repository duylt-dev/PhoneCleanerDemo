package com.pion.phonecleaner.feature.network.speedtest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.component.header.OverlayHeader
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.network.R
import com.pion.phonecleaner.feature.network.speedtest.component.SpeedDial

/**
 * Stateless (`docs/screens/19-network-and-speed-test.md` §2.3). An immersive screen, so the chrome is
 * `OverlayHeader` rather than `PageHeader`.
 *
 * [SpeedTestState.Phase.NotConfigured] is the branch that ships today, and it renders a sentence.
 * There is no dial at 0, no `0 B/s` and no grey placeholder figure: **PENDING OWNER DECISION 2** is
 * unsettled, and a screen that shows a number it did not measure is the defect this cluster is here
 * to avoid.
 */
@Composable
internal fun SpeedTestScreen(
    state: SpeedTestState,
    onIntent: (SpeedTestIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            OverlayHeader(
                title = stringResource(R.string.speed_test_title),
                onBack = { onIntent(SpeedTestIntent.BackPressed) },
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (state.phase) {
                    SpeedTestState.Phase.NotConfigured -> NotMeasuredPanel()
                    SpeedTestState.Phase.Finished -> FinishedPanel(state, onIntent)
                    SpeedTestState.Phase.Idle,
                    SpeedTestState.Phase.Running,
                    -> RunningPanel(state)
                }
            }
        }
    }

    if (state.isAbandonPromptVisible) {
        AppDialog(
            onDismissRequest = { onIntent(SpeedTestIntent.AbandonDismissed) },
            confirmLabel = stringResource(R.string.speed_test_abandon_confirm),
            onConfirm = { onIntent(SpeedTestIntent.AbandonConfirmed) },
            title = stringResource(R.string.speed_test_abandon_title),
            body = stringResource(R.string.speed_test_abandon_body),
            dismissLabel = stringResource(R.string.speed_test_abandon_dismiss),
            onDismiss = { onIntent(SpeedTestIntent.AbandonDismissed) },
        )
    }
}

@Composable
private fun RunningPanel(state: SpeedTestState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        SpeedDial(
            progressPercent = state.progressPercent,
            stage = state.stage,
            bytesPerSecond = state.currentBytesPerSecond,
        )
        Text(
            text = stringResource(R.string.speed_test_running),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A finished run either navigated away with its two numbers or failed. There is no third rendering,
 * because there is no third outcome that could be described without inventing one.
 */
@Composable
private fun FinishedPanel(
    state: SpeedTestState,
    onIntent: (SpeedTestIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = state.error
    if (error == null) {
        RunningPanel(state, modifier)
    } else {
        ErrorCard(
            error = error,
            onRetry = { onIntent(SpeedTestIntent.RetryPressed) },
            modifier = modifier.padding(horizontal = ScreenGutter),
        )
    }
}

@Composable
private fun NotMeasuredPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.speed_test_not_measured_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.speed_test_not_measured_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
