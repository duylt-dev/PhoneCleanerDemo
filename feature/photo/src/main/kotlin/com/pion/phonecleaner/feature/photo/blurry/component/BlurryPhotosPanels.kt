package com.pion.phonecleaner.feature.photo.blurry.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.R as CoreUiR
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.blurry.BlurryPhotosIntent
import com.pion.phonecleaner.feature.photo.blurry.BlurryPhotosState

/**
 * The four things this screen has to say out loud, and nothing it can say silently.
 *
 * The **first** is the one no other screen in this cluster owes: the rows arrive already ticked, so
 * a user who touched nothing is one button away from deleting everything the scan found. Saying so
 * before the delete — not in the confirm dialog, where it would arrive after the decision — is the
 * mitigation the owner's pre-selection decision leaves in place.
 *
 * `skipped` exists because a failed measurement is not a measurement: `BlurDetector.score` returns
 * `Double?` and `null` is excluded, where a `0.0` would be the *blurriest possible* score and would
 * therefore pre-tick every unreadable file for deletion.
 *
 * `pendingConsentUris` is a **state**, not an error: on API 30+ `MediaStore.createDeleteRequest`
 * raises a system dialog, and that is the ordinary path (§0.1).
 */
@Composable
internal fun BlurryPhotosNotices(state: BlurryPhotosState, modifier: Modifier = Modifier) {
    val lines = buildList {
        if (state.isPreselected) add(stringResource(R.string.photo_blurry_preselected_notice))
        if (state.pendingConsentUris.isNotEmpty()) add(stringResource(R.string.photo_consent_pending))
        if (state.consentDeclined) add(stringResource(R.string.photo_consent_declined))
        if (state.failedCount > 0) {
            add(pluralStringResource(R.plurals.photo_delete_failed, state.failedCount, state.failedCount))
        }
        if (state.skipped > 0) {
            add(pluralStringResource(R.plurals.photo_blurry_skipped, state.skipped, state.skipped))
        }
    }
    if (lines.isEmpty()) return
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A dialog is state, so it survives rotation (`LLM.md` §8).
 *
 * Its own copy, not `similar`'s: this delete says **"cannot be undone"** because on this screen the
 * confirm button is reached with a selection the user did not make.
 */
@Composable
internal fun BlurryPhotosDialogs(
    state: BlurryPhotosState,
    onIntent: (BlurryPhotosIntent) -> Unit,
) {
    if (!state.isDeleteConfirmVisible) return
    AppDialog(
        onDismissRequest = { onIntent(BlurryPhotosIntent.DeleteDismissed) },
        confirmLabel = stringResource(CoreUiR.string.action_delete),
        onConfirm = { onIntent(BlurryPhotosIntent.DeleteConfirmed) },
        title = stringResource(R.string.photo_blurry_delete_title),
        body = pluralStringResource(
            if (state.trashEligible) R.plurals.photo_blurry_trash_body else R.plurals.photo_blurry_delete_body,
            state.selectedCount,
            state.selectedCount,
        ),
        dismissLabel = stringResource(CoreUiR.string.action_cancel),
    )
}
