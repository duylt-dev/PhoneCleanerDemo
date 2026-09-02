package com.pion.phonecleaner.feature.applock.applock.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppBottomSheet
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.applock.LockableApp
import com.pion.phonecleaner.feature.applock.R
import com.pion.phonecleaner.feature.applock.applock.AppLockIntent
import com.pion.phonecleaner.feature.applock.applock.AppLockState

/**
 * The two special accesses App Lock needs, asked for **one at a time and named**.
 *
 * A granted access stays visible as granted: the competitor sets a granted card to `GONE`, so
 * granting everything leaves a page holding nothing (`LLM.md` §7.4). There is no grant callback for
 * either of these, so the ticks come from the `ON_START` re-read, not from a result.
 */
@Composable
internal fun AppLockPermissionSheet(
    state: AppLockState,
    onIntent: (AppLockIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismissRequest = { onIntent(AppLockIntent.PermissionSheetDismissed) },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Text(
                text = stringResource(R.string.app_lock_permission_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.app_lock_permission_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GrantButton(
                label = stringResource(R.string.app_lock_permission_usage),
                granted = state.hasUsageStatsPermission,
                onClick = { onIntent(AppLockIntent.GrantUsageStatsTapped) },
            )
            GrantButton(
                label = stringResource(R.string.app_lock_permission_overlay),
                granted = state.hasOverlayPermission,
                onClick = { onIntent(AppLockIntent.GrantOverlayTapped) },
            )
        }
    }
}

@Composable
private fun GrantButton(label: String, granted: Boolean, onClick: () -> Unit) {
    if (granted) {
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text("$label · ${stringResource(R.string.app_lock_permission_granted)}")
        }
    } else {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(label) }
    }
}

/**
 * Unlocking asks first; locking does not. The risk is asymmetric — locking an app you did not mean
 * to costs one PIN entry, unlocking one silently removes a guard you thought was there.
 *
 * It renders from the live list (`AppLockState.pendingUnlockApp` resolves the id against `apps`), so
 * the sheet cannot ask about a row the repository has since changed.
 */
@Composable
internal fun UnlockConfirmSheet(
    app: LockableApp,
    onIntent: (AppLockIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        onDismissRequest = { onIntent(AppLockIntent.UnlockDismissed) },
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Text(
                text = stringResource(R.string.app_lock_unlock_title, app.label),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.app_lock_unlock_body, app.label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { onIntent(AppLockIntent.UnlockConfirmed) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.app_lock_unlock_confirm))
            }
            TextButton(
                onClick = { onIntent(AppLockIntent.UnlockDismissed) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(com.pion.phonecleaner.core.ui.R.string.action_cancel))
            }
        }
    }
}
