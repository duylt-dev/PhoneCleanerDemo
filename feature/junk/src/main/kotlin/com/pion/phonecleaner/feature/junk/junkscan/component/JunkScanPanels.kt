package com.pion.phonecleaner.feature.junk.junkscan.component

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.junk.R
import com.pion.phonecleaner.feature.junk.junkscan.JunkScanIntent
import com.pion.phonecleaner.core.ui.permission.needsAllFilesSettingsPage

/**
 * The three secondary states of `junkscan`, and the ticker.
 *
 * They are here rather than in `JunkScanScreen.kt` because that file is at MVI §4's size rule; the
 * split is by responsibility — the panels a phase selects between — not mid-thought.
 */

/**
 * Denial is a screen with a reason and a button, not `finish()` (Delta S5).
 *
 * The instructions are given **before** the user leaves. The competitor's answer to the same problem
 * is `FadeivatActivity` / `SimcenActivity`, two translucent overlays drawn over the system Settings
 * app from a background activity start — restricted since Android 10 and largely blocked on 14
 * (`LLM.md` §7.5). Those are deleted, not ported.
 */
@Composable
internal fun StoragePermissionPanel(
    onIntent: (JunkScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.junk_permission_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            // Two bodies, because the grant has two shapes: a Settings page with a switch on API 30+,
            // a runtime dialog below it. One string would send half of all users looking for a dialog
            // that never appears. The predicate is the same one `JunkScanRoute` picks its launcher
            // with, so the words and the button can never describe different flows.
            text = stringResource(
                if (needsAllFilesSettingsPage()) {
                    R.string.junk_permission_body_all_files
                } else {
                    R.string.junk_permission_body
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { onIntent(JunkScanIntent.GrantStorageTapped) }) {
            Text(stringResource(R.string.junk_permission_grant))
        }
    }
}

/**
 * A scan failure is a failure, with a retry. The competitor's
 * `catch (Exception) { printStackTrace(); scanEnd = true; finish(); }` makes a failure
 * indistinguishable from a Back press (`TaribrActivity.java:301-305`, Delta S6).
 */
@Composable
internal fun ScanFailedPanel(
    error: AppError?,
    onIntent: (JunkScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.xxl)) {
        ErrorCard(
            error = error ?: AppError.Unexpected(),
            onRetry = { onIntent(JunkScanIntent.RetryTapped) },
        )
    }
}

/**
 * "Stop scanning?" — the confirm that replaces a blocked Back and a Toast
 * (`TaribrActivity.java:447-454`, Delta S7).
 *
 * A dialog is state, so it survives rotation (`LLM.md` §7.4). All 18 competitor dialogs vanish on
 * rotation, structurally.
 */
@Composable
internal fun StopScanDialog(onIntent: (JunkScanIntent) -> Unit) {
    AppDialog(
        onDismissRequest = { onIntent(JunkScanIntent.StopDismissed) },
        confirmLabel = stringResource(R.string.junk_scan_stop_confirm),
        onConfirm = { onIntent(JunkScanIntent.StopConfirmed) },
        title = stringResource(R.string.junk_scan_stop_title),
        body = stringResource(R.string.junk_scan_stop_body),
        dismissLabel = stringResource(com.pion.phonecleaner.core.ui.R.string.action_cancel),
        onDismiss = { onIntent(JunkScanIntent.StopDismissed) },
    )
}

/**
 * The file-name ticker, as **its own leaf composable**.
 *
 * `currentPath` changes on nearly every emission, so drawing it here keeps the byte counter and the
 * phase illustration out of that recomposition (`docs/screens/12-junk-cleaning.md` §3.3, MVI §8).
 */
@Composable
internal fun ScanPathTicker(path: String, modifier: Modifier = Modifier) {
    Text(
        text = path,
        modifier = modifier.fillMaxWidth().basicMarquee(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
    )
}
