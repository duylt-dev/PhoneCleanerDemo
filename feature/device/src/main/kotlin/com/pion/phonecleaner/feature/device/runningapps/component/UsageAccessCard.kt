package com.pion.phonecleaner.feature.device.runningapps.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.device.R

/**
 * ### PENDING OWNER DECISION 3 — the seam
 *
 * `docs/screens/18-device-battery-and-apps.md` §0.1, tracked as `docs/system-architecture.md` §10.1
 * **P1**. It is **not settled**, and this card settles nothing: it is the ungranted path rendered as
 * a real state so that both outcomes stay one edit away.
 *
 * | If the owner picks | What changes |
 * |---|---|
 * | **A** — keep `PACKAGE_USAGE_STATS` and make the list real | `PackageManagerRunningAppsRepository.stoppableApps()` swaps to `UsageStatsManager.queryEvents`, the permission is declared, and this card becomes a precondition instead of a rationale. No contract, ViewModel or screen changes |
 * | **B** — drop the gate | this file and one `when` arm are deleted |
 *
 * **The permission is declared in no manifest** while the decision is open, and nothing in this app
 * reads usage statistics. `Settings.ACTION_USAGE_ACCESS_SETTINGS` opens without a declaration —
 * only *reading* the stats needs one — so the grant action below is honest today and stays honest
 * either way.
 *
 * The copy says what the list **is** and what usage access **would** add. It does not say the app
 * will speed anything up, free memory or extend battery life: this cluster is exactly where the
 * competitor makes those claims (`LLM.md` §1, §5).
 */
@Composable
internal fun UsageAccessCard(
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.running_apps_usage_access_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.running_apps_usage_access_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onGrant) {
                    Text(stringResource(R.string.running_apps_usage_access_grant))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.running_apps_usage_access_dismiss))
                }
            }
        }
    }
}
