package com.pion.phonecleaner.feature.settings.permissioncentre.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppBottomSheet
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.settings.R
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCard
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCentreIntent
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCopy

/**
 * **One** sheet, driven by `state.rationaleFor`, in place of the competitor's four `nc.*`
 * `AlertDialog` subclasses (`docs/screens/20-settings-language-and-push.md` §5.3, `LLM.md` §7.4).
 *
 * Its copy is selected from the enum, and the numbered steps — for a grant taken in a system screen —
 * are shown **before** the user leaves. That is the whole replacement for the two translucent
 * activities the competitor draws over the system Settings app (§5.4 delta 4).
 */
@Composable
internal fun PermissionRationaleSheet(
    card: PermissionCard,
    onIntent: (PermissionCentreIntent) -> Unit,
) {
    AppBottomSheet(onDismissRequest = { onIntent(PermissionCentreIntent.RationaleDismissed) }) {
        Text(
            text = stringResource(PermissionCopy.titleRes(card.permission)),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
        Text(
            text = stringResource(PermissionCopy.bodyRes(card.permission)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PermissionCopy.stepsRes(card.permission)?.let { steps ->
            Text(
                text = stringResource(R.string.settings_permission_steps_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
            )
            Text(
                text = stringResource(steps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
        ) {
            TextButton(onClick = { onIntent(PermissionCentreIntent.RationaleDismissed) }) {
                Text(stringResource(R.string.settings_permissions_cancel))
            }
            Button(onClick = { onIntent(PermissionCentreIntent.RationaleConfirmed) }) {
                Text(stringResource(R.string.settings_permissions_grant))
            }
        }
    }
}
