package com.pion.phonecleaner.feature.antivirus.scan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.antivirus.R
import com.pion.phonecleaner.feature.antivirus.component.PoweredByTrustlook
import com.pion.phonecleaner.feature.antivirus.scan.component.DataConsentDialog
import com.pion.phonecleaner.feature.antivirus.scan.component.ScanFailedPanel
import com.pion.phonecleaner.feature.antivirus.scan.component.ScanGatePanel
import com.pion.phonecleaner.feature.antivirus.scan.component.ScanProgressPanel
import com.pion.phonecleaner.feature.antivirus.scan.component.StopScanDialog

/**
 * Stateless: `(state, onIntent) -> Unit`, never the ViewModel (MVI §4). It reads no `viewModel` and
 * no `LocalContext` for data, so every phase and every overlay is reachable in a `@Preview`.
 *
 * It renders `PageHeader` and never writes its own header (MVI §11); the outermost container takes
 * the inset, because in landscape the cutout moves to one side and the whole page moves with it.
 *
 * **Three panels, chosen in one `when`.** A failure is a rendered state that replaces the content,
 * not the competitor's modal whose only button calls `finish()` — DELIBERATE DEVIATION from
 * `docs/screens/15-antivirus.md` §1.3, which draws it as `ScanErrorDialog`. §1.5's own complaint
 * about that dialog is that it is a dead end, and `ScanFailure.SdkUnavailable` — the arm this build
 * actually reaches — has no retry to offer, so a modal over a dead screen would be exactly the
 * shape being replaced. The junk cluster's `ScanFailedPanel` is the same decision, already shipped.
 *
 * **The attribution sits outside that `when` on purpose**: it is a licence requirement, and a
 * placement inside one arm would hide it in every other state — including the failed one this build
 * reaches every time.
 */
@Composable
internal fun AntivirusScanScreen(
    state: AntivirusScanState,
    onIntent: (AntivirusScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val failure = state.failure
    val gate = state.blockingGate
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.antivirus_title),
                onBack = { onIntent(AntivirusScanIntent.BackPressed) },
            )
            Box(Modifier.weight(1f)) {
                when {
                    failure != null -> ScanFailedPanel(failure, onIntent)
                    // While the consent dialog is up, the gate behind it is that same gate: drawing
                    // the "you declined" panel under an unanswered question states an answer nobody
                    // gave.
                    gate != null && !state.isConsentDialogVisible -> ScanGatePanel(gate, onIntent)
                    else -> ScanProgressPanel(state)
                }
            }
            PoweredByTrustlook(Modifier.align(Alignment.CenterHorizontally))
        }
        if (state.isConsentDialogVisible) DataConsentDialog(onIntent)
        if (state.isStopConfirmVisible) StopScanDialog(onIntent)
    }
}
