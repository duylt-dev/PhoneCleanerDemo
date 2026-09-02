package com.pion.phonecleaner.feature.antivirus.scan.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.feature.antivirus.R
import com.pion.phonecleaner.feature.antivirus.scan.AntivirusScanIntent

/**
 * The data-collection disclosure (`docs/screens/15-antivirus.md` §0.5, §1.3).
 *
 * `dismissible = false` keeps `dismissOnBackPress` and `dismissOnClickOutside` off: the
 * competitor's non-cancellable consent is **correct** here and is kept. An unanswered disclosure
 * that can be dismissed by tapping the scrim is an answer nobody gave, and the two DataStore keys
 * behind it record exactly one of two answers.
 *
 * Its paragraphs are `stringResource`s, never built in the ViewModel.
 *
 * UNKNOWN — the two policy links. §1.3 says the dialog's links raise `OpenUrl`, and
 * `AntivirusScanIntent.ConsentLinkTapped` carries a URL, but **no URL exists to raise**:
 * `LegalDocument`'s own KDoc says the address is resolved by `LegalDocumentUrls` in `:data`, and
 * that type is not written yet (grepped `:data`, `:domain`, `:core`, `:feature` and the shared API
 * digest). No link is drawn rather than a fabricated address; the effect stays declared and
 * collected, and the row is added the moment a URL source lands.
 */
@Composable
internal fun DataConsentDialog(onIntent: (AntivirusScanIntent) -> Unit) {
    AppDialog(
        onDismissRequest = { onIntent(AntivirusScanIntent.ConsentRejected) },
        confirmLabel = stringResource(R.string.antivirus_consent_accept),
        onConfirm = { onIntent(AntivirusScanIntent.ConsentAccepted) },
        title = stringResource(R.string.antivirus_consent_title),
        body = stringResource(R.string.antivirus_consent_body),
        dismissLabel = stringResource(R.string.antivirus_consent_reject),
        onDismiss = { onIntent(AntivirusScanIntent.ConsentRejected) },
        dismissible = false,
    )
}

/**
 * Escapable, unlike the consent: stopping is the destructive answer here, and dismissing it keeps
 * the scan running (§1.3). `StopConfirmed` cancels the scan job, which cancels the flow, which runs
 * the client's `awaitClose`.
 */
@Composable
internal fun StopScanDialog(onIntent: (AntivirusScanIntent) -> Unit) {
    AppDialog(
        onDismissRequest = { onIntent(AntivirusScanIntent.StopDismissed) },
        confirmLabel = stringResource(R.string.antivirus_stop_confirm),
        onConfirm = { onIntent(AntivirusScanIntent.StopConfirmed) },
        title = stringResource(R.string.antivirus_stop_title),
        body = stringResource(R.string.antivirus_stop_body),
        dismissLabel = stringResource(R.string.antivirus_stop_dismiss),
        onDismiss = { onIntent(AntivirusScanIntent.StopDismissed) },
    )
}
