package com.pion.phonecleaner.feature.antivirus.result.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import com.pion.phonecleaner.feature.antivirus.R
import com.pion.phonecleaner.feature.antivirus.result.AntivirusResultIntent

/**
 * The destructive confirm (`docs/screens/15-antivirus.md` §2.3).
 *
 * **Escapable, unlike the consent dialog** — a destructive confirm should be, and every dismissal
 * path lands on the same `RemovalDismissed`, so `pendingRemovalMd5` is cleared in every arm. The
 * competitor clears its equivalent field only on the non-empty render branch, so removing the last
 * row leaves it set and a stray broadcast shows a second success (§2.5).
 *
 * **The body is the finding's own summary** — the English description from the vendor's table,
 * already parsed and rendered nowhere by the competitor, which shows a level with no reason. The
 * second line says what the button will actually do, and those are two different things: an
 * installed app can only be removed by the system uninstall screen, while a loose file is deleted
 * here.
 */
@Composable
internal fun RemoveFindingDialog(
    finding: ThreatVerdict,
    onIntent: (AntivirusResultIntent) -> Unit,
) {
    AppDialog(
        onDismissRequest = { onIntent(AntivirusResultIntent.RemovalDismissed) },
        confirmLabel = stringResource(R.string.antivirus_remove_confirm),
        onConfirm = { onIntent(AntivirusResultIntent.RemovalConfirmed) },
        title = stringResource(R.string.antivirus_remove_title, finding.label),
        dismissLabel = stringResource(R.string.antivirus_remove_dismiss),
        onDismiss = { onIntent(AntivirusResultIntent.RemovalDismissed) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            if (finding.summary.isNotBlank()) {
                Text(finding.summary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = stringResource(
                    if (finding.isInstalledApp) {
                        R.string.antivirus_remove_installed_note
                    } else {
                        R.string.antivirus_remove_file_note
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
