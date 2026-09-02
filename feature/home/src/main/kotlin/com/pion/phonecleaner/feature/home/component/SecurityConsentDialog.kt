package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.feature.home.R

/**
 * The gate in front of the app safety check — the competitor's `nc.b1` over `dorspic.xml`, and the
 * only entry point on this screen with a pre-flight.
 *
 * **It is a gate, not decoration.** TrustLook is licensed, so the *"Powered by Trustlook"*
 * attribution is a hard UI requirement on every surface that shows a scan result
 * (`LLM.md` §5), and it is carried here as well because this is where the third party is first
 * named. Agreeing persists the consent **before** navigation is raised — the order is the
 * reducer's, and the test matrix asserts it (`docs/screens/11-home.md` §2, §4.1).
 *
 * Two deliberate departures from the original:
 *
 *  1. **Dismissible.** `nc.b1` is `setCancelable(false)`; here a dismissal is a rejection, which is
 *     the conservative reading of "the user did not agree".
 *  2. **The two links are `LegalDocument` values, not URL strings.** The competitor's document
 *     screen takes a free-form `strUrl` extra although only two literal URLs ever reach it from
 *     four call sites; a general-purpose URL loader is attack surface for no benefit. The URL is
 *     resolved in `:data`, and an unknown value cannot be expressed once the argument is an enum.
 *
 * UNKNOWN — what the check sends to the third party is not stated by any source available here.
 * Looked for it in `docs/screens/11-home.md` §2 (which describes the gate but not the disclosure)
 * and in `docs/reverse-engineering/11-home.md` §7.2, which records only that the competitor's copy
 * "names the third party outright" and quotes it as evidence. The body therefore names the third
 * party and points at the two documents, and claims nothing about the data flow.
 */
@Composable
internal fun SecurityConsentDialog(
    onAgree: () -> Unit,
    onReject: () -> Unit,
    onOpenDocument: (LegalDocument) -> Unit,
) {
    AppDialog(
        onDismissRequest = onReject,
        confirmLabel = stringResource(R.string.home_consent_agree),
        onConfirm = onAgree,
        title = stringResource(R.string.home_consent_title),
        body = stringResource(R.string.home_consent_body),
        icon = { Icon(Icons.Filled.Security, contentDescription = null) },
        dismissLabel = stringResource(R.string.home_consent_reject),
        onDismiss = onReject,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TextButton(onClick = { onOpenDocument(LegalDocument.TermsOfService) }) {
                Text(stringResource(R.string.home_consent_terms))
            }
            TextButton(onClick = { onOpenDocument(LegalDocument.PrivacyPolicy) }) {
                Text(stringResource(R.string.home_consent_privacy))
            }
        }
        Text(
            text = stringResource(R.string.home_consent_powered_by),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
