package com.pion.phonecleaner.feature.files.appmanager.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppBottomSheet
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.files.R

/**
 * The explanation shown **before** the user is handed out to Settings (§5.5).
 *
 * It replaces a translucent coach mark the competitor draws over the system Settings app from a
 * 1 500 ms delayed `Runnable` on a bare `Handler` — a background activity start over another app.
 *
 * The copy states what the grant buys and what still works without it, and it claims nothing about
 * speed, memory or protection.
 */
@Composable
internal fun UsageAccessRationaleSheet(
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = stringResource(R.string.app_manager_usage_access_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onContinue) {
                Text(stringResource(R.string.app_manager_usage_access_grant))
            }
        }
    }
}
