package com.pion.phonecleaner.feature.notification.hidingsettings.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.notification.R

/**
 * The master switch, and the inline hint that replaces a silent write
 * (`docs/screens/17-notification-and-permissions.md` §2.3, §2.5).
 *
 * When [enabledCount] is zero the card says so and **writes nothing**. The competitor, on the tap that
 * disables the last app, silently writes the master switch off: a per-app action mutating a global
 * setting, with no undo and no message, after which the list vanishes.
 */
@Composable
internal fun MasterHidingCard(
    checked: Boolean,
    enabledCount: Int,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stateLabel = stringResource(
        if (checked) R.string.notification_hiding_master_on else R.string.notification_hiding_master_off,
    )
    Card(modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(
                    text = stringResource(R.string.notification_hiding_master_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.semantics { stateDescription = stateLabel },
                )
            }
            Text(
                text = stringResource(R.string.notification_hiding_master_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (checked && enabledCount == 0) {
                Text(
                    text = stringResource(R.string.notification_hiding_none_selected),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
