package com.pion.phonecleaner.feature.photo.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.R as CoreUiR
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SelectionBar
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.component.PHOTO_GRID_COLUMNS
import com.pion.phonecleaner.feature.photo.component.PhotoCompletionPanel
import com.pion.phonecleaner.feature.photo.component.PhotoScanPanel
import com.pion.phonecleaner.feature.photo.component.photoGroupItems

/**
 * `docs/screens/13-photo-and-media.md` §5.3.
 *
 * The reassurance line is permanent and comes from a string resource, so it reads correctly in every
 * locale rather than being assembled at a call site.
 */
@Composable
internal fun PhotoPrivacyScreen(
    state: PhotoPrivacyState,
    onIntent: (PhotoPrivacyIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Hoisted once, above the lane (`LLM.md` §8).
    val onTogglePhoto: (PhotoId) -> Unit = { id -> onIntent(PhotoPrivacyIntent.PhotoToggled(id)) }
    val onToggleMonth: (String) -> Unit = { key -> onIntent(PhotoPrivacyIntent.MonthToggled(key)) }
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.photo_privacy_title),
                onBack = { onIntent(PhotoPrivacyIntent.BackPressed) },
            )
            Text(
                text = stringResource(R.string.photo_privacy_reassurance),
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.sm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state.phase) {
                ToolPhase.Scanning -> PhotoScanPanel(
                    label = stringResource(R.string.photo_scanning),
                    done = state.scanned,
                    total = state.toScan,
                )

                ToolPhase.Completing -> PhotoCompletionPanel(
                    onFinished = { onIntent(PhotoPrivacyIntent.CompletionAnimationFinished) },
                )

                else -> Unit
            }
            state.strip?.let { StripOverlay(it) }
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = { onIntent(PhotoPrivacyIntent.ScreenStarted) },
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )
            }
            if (state.showEmptyState) {
                EmptyState(message = stringResource(R.string.photo_privacy_empty))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(PHOTO_GRID_COLUMNS),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = ScreenGutter,
                        end = ScreenGutter,
                        bottom = PageSpacing.listBottom,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    photoGroupItems(
                        groups = state.groups,
                        selectedIds = state.selectedIds,
                        onToggleGroup = onToggleMonth,
                        onTogglePhoto = onTogglePhoto,
                    )
                }
            }
            SelectionBar(
                selectedCount = state.selectedCount,
                selectedBytes = state.selectedBytes,
                onSelectAll = { onIntent(PhotoPrivacyIntent.SelectAllToggled) },
                onDelete = { onIntent(PhotoPrivacyIntent.ClearPressed) },
                actionLabel = stringResource(R.string.photo_privacy_action),
            )
        }
    }
    PhotoPrivacyDialogs(state, onIntent)
}

/** A determinate bar: `done`/`total` are counted, never a timer (§5.3). */
@Composable
private fun StripOverlay(progress: StripProgress) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.photo_scan_progress, progress.done, progress.total),
            style = MaterialTheme.typography.labelMedium,
        )
        LinearProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
            modifier = Modifier.fillMaxWidth(),
        )
        if (progress.failedCount > 0) {
            Text(
                text = pluralStringResource(
                    R.plurals.photo_failed_count,
                    progress.failedCount,
                    progress.failedCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A dialog is state, so it survives rotation (`LLM.md` §8). */
@Composable
private fun PhotoPrivacyDialogs(state: PhotoPrivacyState, onIntent: (PhotoPrivacyIntent) -> Unit) {
    if (state.isClearConfirmVisible) {
        AppDialog(
            onDismissRequest = { onIntent(PhotoPrivacyIntent.ClearDismissed) },
            confirmLabel = stringResource(R.string.photo_privacy_action),
            onConfirm = { onIntent(PhotoPrivacyIntent.ClearConfirmed) },
            title = stringResource(R.string.photo_privacy_confirm_title),
            // A plural with a count, never a hand-appended "s" (§5.3).
            body = pluralStringResource(
                R.plurals.photo_privacy_confirm_body,
                state.selectedCount,
                state.selectedCount,
            ),
            dismissLabel = stringResource(CoreUiR.string.action_cancel),
        )
    }
    if (state.isStopConfirmVisible) {
        AppDialog(
            onDismissRequest = { onIntent(PhotoPrivacyIntent.StopDismissed) },
            confirmLabel = stringResource(R.string.photo_action_stop),
            onConfirm = { onIntent(PhotoPrivacyIntent.StopConfirmed) },
            title = stringResource(R.string.photo_privacy_stop_title),
            body = stringResource(R.string.photo_privacy_stop_body),
            dismissLabel = stringResource(CoreUiR.string.action_cancel),
        )
    }
}
