package com.pion.phonecleaner.feature.home.component

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
import com.pion.phonecleaner.feature.home.R

/**
 * The notification opt-in — the competitor's `nc.w` over `foasift.xml`, one of the three presenters
 * that move the window to `Gravity.BOTTOM`, which is why it is an `AppBottomSheet` and not an
 * `AppDialog` (`docs/screens/21` §3.1).
 *
 * **Dismissing is an answer, not an absence of one.** Both exits raise
 * `NotificationSheetAnswered`, so `hasOfferedNotificationSheet` is written *after* the user
 * responds. The competitor writes its equivalent latch **before** the dialog is shown
 * (`CucurtagActivity:678`), so a process death or a swipe-away burns the only ask the app ever
 * makes (`LLM.md` §7.4).
 */
@Composable
internal fun NotificationPermissionSheet(
    onTurnOn: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.home_notification_sheet_title),
            modifier = Modifier.padding(top = Spacing.lg),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.home_notification_sheet_body),
            modifier = Modifier.padding(top = Spacing.md),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md, Alignment.End),
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.home_notification_sheet_dismiss))
            }
            Button(onClick = onTurnOn) {
                Text(stringResource(R.string.home_notification_sheet_confirm))
            }
        }
    }
}
