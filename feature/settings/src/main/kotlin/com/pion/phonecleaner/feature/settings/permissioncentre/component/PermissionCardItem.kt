package com.pion.phonecleaner.feature.settings.permissioncentre.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.settings.R
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCard
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCentreIntent
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCopy

/**
 * One ungranted permission. Replaces a 70-line XML card block that the competitor repeats four times
 * inside a `NestedScrollView` over a static `LinearLayout`
 * (`docs/screens/20-settings-language-and-push.md` §5.3).
 *
 * **One `Card` with `onClick`, and a real trailing `TextButton`.** The competitor puts the click on
 * the card and adds a decorative "Open" button that is *not* the target, plus a `0dp` `gone` "Scan"
 * `TextView` — an ambiguous target and two dead views (§5.4 delta 6). Here both the card and the
 * button raise the same intent, so whichever the user aims at is the one that works.
 *
 * `onIntent` is taken as-is; the card is rendered from a keyed `items {}` and must stay skippable
 * (`LLM.md` §8).
 */
@Composable
internal fun PermissionCardItem(
    card: PermissionCard,
    onIntent: (PermissionCentreIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { onIntent(PermissionCentreIntent.CardTapped(card.permission)) },
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(PermissionCopy.titleRes(card.permission)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(PermissionCopy.bodyRes(card.permission)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (card.wasDeclined) {
                Text(
                    text = stringResource(R.string.settings_permissions_declined),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onIntent(PermissionCentreIntent.CardTapped(card.permission)) },
                ) {
                    Text(stringResource(R.string.settings_permissions_grant))
                }
            }
        }
    }
}

/**
 * One already-granted permission, rendered in its own section rather than removed.
 *
 * It is not clickable: revoking is the system's screen, and a row that silently did nothing would be
 * the competitor's empty `onClick` again (§1.4 delta 2).
 */
@Composable
internal fun GrantedRow(card: PermissionCard, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(PermissionCopy.titleRes(card.permission)),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.settings_permissions_granted_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
