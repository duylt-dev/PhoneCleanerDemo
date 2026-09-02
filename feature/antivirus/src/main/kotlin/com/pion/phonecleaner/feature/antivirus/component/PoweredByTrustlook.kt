package com.pion.phonecleaner.feature.antivirus.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.antivirus.R

/**
 * The vendor attribution. **MANDATORY, and not decoration** — a licence requirement
 * (`LLM.md` §5, `docs/screens/15-antivirus.md` header). Do not reword the string, do not translate
 * it, and do not put it anywhere a state can hide it.
 *
 * It lives in the cluster-shared `component/` package rather than in one screen's, because both
 * screens display it: the scan screen renders it **outside** the phase `when`, so the arm this
 * build actually reaches — `ScanFailure.SdkUnavailable`, raised because the SDK has no Maven
 * coordinate — still shows it, and the result screen renders it under the list.
 */
@Composable
internal fun PoweredByTrustlook(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.antivirus_powered_by),
        modifier = modifier.padding(vertical = Spacing.md),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
