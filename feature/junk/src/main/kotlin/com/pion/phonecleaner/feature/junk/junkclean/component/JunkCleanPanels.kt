package com.pion.phonecleaner.feature.junk.junkclean.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.junk.R
import com.pion.phonecleaner.feature.junk.junkclean.JunkCleanIntent

/**
 * "Stop cleaning?" Cancelling is safe: the use case has already recorded every path it freed.
 *
 * The competitor's `onDestroy` cancels its loop with no journal — the files stay deleted,
 * `cleanedSize` is lost, and the caches are never reset (Delta C10).
 */
@Composable
internal fun StopCleanDialog(onIntent: (JunkCleanIntent) -> Unit) {
    AppDialog(
        onDismissRequest = { onIntent(JunkCleanIntent.StopDismissed) },
        confirmLabel = stringResource(R.string.junk_clean_stop_confirm),
        onConfirm = { onIntent(JunkCleanIntent.StopConfirmed) },
        title = stringResource(R.string.junk_clean_stop_title),
        body = stringResource(R.string.junk_clean_stop_body),
        dismissLabel = stringResource(com.pion.phonecleaner.core.ui.R.string.action_cancel),
        onDismiss = { onIntent(JunkCleanIntent.StopDismissed) },
    )
}

/**
 * Access was revoked between the two screens (Delta C2).
 *
 * It offers Back as well as Grant, because §5.1's intent set has no way for the screen to learn that
 * a grant was given — see the UNKNOWN on `JunkCleanViewModel.onIntent`. Without the second button a
 * user who granted would be looking at a screen that could not act on it.
 */
@Composable
internal fun StorageAccessLostPanel(
    onIntent: (JunkCleanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.junk_clean_access_lost_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.junk_clean_access_lost_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { onIntent(JunkCleanIntent.GrantStorageTapped) }) {
            Text(stringResource(R.string.junk_permission_grant))
        }
        TextButton(onClick = { onIntent(JunkCleanIntent.BackPressed) }) {
            Text(stringResource(com.pion.phonecleaner.core.ui.R.string.action_back))
        }
    }
}

/** A clean failure is a failure. Back is offered; there is nothing to retry without a new scan. */
@Composable
internal fun CleanFailedPanel(
    error: AppError?,
    onIntent: (JunkCleanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.xxl)) {
        ErrorCard(
            error = error ?: AppError.Unexpected(),
            onRetry = { onIntent(JunkCleanIntent.BackPressed) },
        )
    }
}
