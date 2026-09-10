package com.pion.phonecleaner.feature.photo.similar.component

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
import com.pion.phonecleaner.feature.photo.similar.SimilarPhotosIntent
import com.pion.phonecleaner.feature.photo.similar.SimilarPhotosState

/**
 * The three things this screen has to say out loud, and nothing it can say silently.
 *
 * `skipped` exists because a hash failure is not a similarity: `PerceptualHasher.hash` returns
 * `Long?` and `null` is excluded from grouping, where the competitor's `b0.h` returns `0L` and drops
 * every unreadable file into one "similar" group (§1.4). Saying how many were left out is what makes
 * that honest.
 *
 * `pendingConsentUris` is a **state**, not an error: on API 30+ `MediaStore.createDeleteRequest`
 * raises a system dialog, and that is the ordinary path (§0.1).
 */
@Composable
internal fun SimilarPhotosNotices(state: SimilarPhotosState, modifier: Modifier = Modifier) {
    val lines = buildList {
        if (state.pendingConsentUris.isNotEmpty()) add(stringResource(R.string.photo_consent_pending))
        if (state.consentDeclined) add(stringResource(R.string.photo_consent_declined))
        if (state.failedCount > 0) {
            add(pluralStringResource(R.plurals.photo_delete_failed, state.failedCount, state.failedCount))
        }
        if (state.skipped > 0) {
            add(pluralStringResource(R.plurals.photo_similar_skipped, state.skipped, state.skipped))
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

/** A dialog is state, so it survives rotation (`LLM.md` §8). */
@Composable
internal fun SimilarPhotosDialogs(
    state: SimilarPhotosState,
    onIntent: (SimilarPhotosIntent) -> Unit,
) {
    if (!state.isDeleteConfirmVisible) return
    AppDialog(
        onDismissRequest = { onIntent(SimilarPhotosIntent.DeleteDismissed) },
        confirmLabel = stringResource(CoreUiR.string.action_delete),
        onConfirm = { onIntent(SimilarPhotosIntent.DeleteConfirmed) },
        title = stringResource(R.string.photo_album_delete_title),
        body = pluralStringResource(
            if (state.trashEligible) R.plurals.photo_album_trash_body else R.plurals.photo_album_delete_body,
            state.selectedCount,
            state.selectedCount,
        ),
        dismissLabel = stringResource(CoreUiR.string.action_cancel),
    )
}
