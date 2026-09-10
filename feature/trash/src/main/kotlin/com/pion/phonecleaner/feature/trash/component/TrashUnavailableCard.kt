package com.pion.phonecleaner.feature.trash.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.trash.R

/**
 * The no-grant explainer, drawn above the list rather than replacing it: rows trashed while the grant
 * was held are still there, only the actions on them are disabled (`TrashState.isTrashAvailable`'s own
 * KDoc). Shape copied from `HidingPausedBanner`
 * (`feature/notification/hiddenlist/component/HiddenListChrome.kt`) — a persistent card explaining why
 * a feature is off, with one action.
 */
@Composable
internal fun TrashUnavailableCard(onAllowAccess: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = stringResource(R.string.trash_unavailable_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.trash_unavailable_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onAllowAccess, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.trash_unavailable_action))
            }
        }
    }
}
