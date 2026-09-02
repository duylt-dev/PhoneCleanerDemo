package com.pion.phonecleaner.feature.antivirus.scan.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.security.ScanFailure
import com.pion.phonecleaner.feature.antivirus.R
import com.pion.phonecleaner.feature.antivirus.scan.AntivirusScanIntent
import com.pion.phonecleaner.feature.antivirus.scan.ScanGate
import com.pion.phonecleaner.core.ui.R as CoreUiR

/**
 * The blocked screen — one panel per [ScanGate], in the competitor's own gate order
 * (`docs/screens/15-antivirus.md` §1.1).
 *
 * **The storage gate is not a trap.** Its one action asks; whatever the answer, the reducer's next
 * pass runs the scan with the coverage that answer allows, and the screen says what was skipped.
 * The competitor's equivalent is a settings funnel with no way past it (§1.5).
 *
 * **The consent gate carries no action, and that is deliberate.** A recorded rejection is
 * remembered rather than re-asked on every entry (`ScanConsentState`'s KDoc, §0.5), and the
 * contract this screen renders has no intent for re-opening the disclosure — so the panel explains
 * and does not pretend. Reversing a "no" is reported as an open item rather than invented here.
 */
@Composable
internal fun ScanGatePanel(
    gate: ScanGate,
    onIntent: (AntivirusScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (gate) {
        ScanGate.Network -> MessagePanel(
            title = stringResource(R.string.antivirus_gate_network_title),
            body = stringResource(R.string.antivirus_gate_network_body),
            actionLabel = stringResource(CoreUiR.string.action_retry),
            onAction = { onIntent(AntivirusScanIntent.RetryPressed) },
            modifier = modifier,
        )

        ScanGate.Consent -> MessagePanel(
            title = stringResource(R.string.antivirus_gate_consent_title),
            body = stringResource(R.string.antivirus_gate_consent_body),
            actionLabel = null,
            onAction = null,
            modifier = modifier,
        )

        ScanGate.StoragePermission -> MessagePanel(
            title = stringResource(R.string.antivirus_gate_storage_title),
            body = stringResource(R.string.antivirus_gate_storage_body),
            actionLabel = stringResource(R.string.antivirus_gate_storage_grant),
            onAction = { onIntent(AntivirusScanIntent.GrantStoragePressed) },
            modifier = modifier,
        )
    }
}

/**
 * A failure, rendered — **one line per arm, never one line for all nine SDK codes**
 * (§0.2, and §1.5's row on the competitor's single sentence).
 *
 * The retry button appears only when retrying can succeed: `ScanFailure.SdkUnavailable` is a
 * missing dependency, not a transient fault, and a button that can only fail again is the
 * competitor's dialog wearing a different label.
 */
@Composable
internal fun ScanFailedPanel(
    failure: ScanFailure,
    onIntent: (AntivirusScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MessagePanel(
        title = stringResource(R.string.antivirus_failed_title),
        body = stringResource(failure.messageRes()),
        actionLabel = stringResource(CoreUiR.string.action_retry).takeIf { failure.isRetryable },
        onAction = { onIntent(AntivirusScanIntent.RetryPressed) },
        modifier = modifier,
    )
}

@StringRes
private fun ScanFailure.messageRes(): Int = when (this) {
    ScanFailure.NoNetwork -> R.string.antivirus_failed_no_network
    ScanFailure.Timeout -> R.string.antivirus_failed_timeout
    ScanFailure.BadApiKey -> R.string.antivirus_failed_bad_key
    ScanFailure.ServerError -> R.string.antivirus_failed_server
    ScanFailure.NothingToScan -> R.string.antivirus_failed_nothing
    ScanFailure.SdkUnavailable -> R.string.antivirus_scan_unavailable
    ScanFailure.Unknown -> R.string.antivirus_failed_unknown
}

/** The one arrangement behind every blocked and failed state, so five panels cannot drift apart. */
@Composable
private fun MessagePanel(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenGutter)
            .padding(top = PageSpacing.headerToContent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
