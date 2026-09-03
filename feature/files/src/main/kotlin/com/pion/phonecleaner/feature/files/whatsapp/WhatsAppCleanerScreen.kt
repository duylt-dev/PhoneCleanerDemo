package com.pion.phonecleaner.feature.files.whatsapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SelectionBar
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.component.ConfirmDialogHost
import com.pion.phonecleaner.feature.files.component.ToolBanner
import com.pion.phonecleaner.feature.files.component.ToolOverlay
import com.pion.phonecleaner.feature.files.whatsapp.component.BucketDetailSheet
import com.pion.phonecleaner.feature.files.whatsapp.component.BucketTile
import com.pion.phonecleaner.feature.files.whatsapp.component.CleaningOverlay

/**
 * `docs/screens/14-file-tools-and-app-manager.md` §6.3.
 *
 * `onIntent` is passed down as-is: a lambda allocated inside `items {}` is a new instance every
 * recomposition and defeats the skip for every tile (`LLM.md` §8).
 */
@Composable
internal fun WhatsAppCleanerScreen(
    state: WhatsAppCleanerState,
    onIntent: (WhatsAppCleanerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().screenInsetsPadding()) {
            Column(Modifier.fillMaxSize()) {
                PageHeader(
                    title = stringResource(R.string.whatsapp_title),
                    onBack = { onIntent(WhatsAppCleanerIntent.BackPressed) },
                )
                if (!state.legacyRootGranted) {
                    // A SAF tree, never MANAGE_EXTERNAL_STORAGE (§0.2).
                    ToolBanner(
                        message = stringResource(R.string.whatsapp_grant_legacy_root),
                        actionLabel = stringResource(R.string.files_coverage_grant_more),
                        onAction = { onIntent(WhatsAppCleanerIntent.GrantLegacyRootPressed) },
                    )
                }
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(WhatsAppCleanerIntent.ScreenStarted) },
                        modifier = Modifier.padding(horizontal = ScreenGutter),
                    )
                }
                WhatsAppBody(state, onIntent, Modifier.weight(1f))
                if (state.buckets.isNotEmpty()) {
                    SelectionBar(
                        selectedCount = state.selectedFileCount,
                        selectedBytes = state.selectedBytes,
                        onSelectAll = { onIntent(WhatsAppCleanerIntent.AllBucketsToggled) },
                        onDelete = { onIntent(WhatsAppCleanerIntent.CleanPressed) },
                        actionLabel = stringResource(R.string.whatsapp_clean_action),
                    )
                }
            }
            // While a clean runs, CleaningOverlay is the overlay: it reports bytes, not a spinner.
            ToolOverlay(
                phase = if (state.cleaning != null) ToolPhase.Idle else state.phase,
                onCompletionFinished = {
                    onIntent(WhatsAppCleanerIntent.CompletionAnimationFinished)
                },
                scanningLabel = stringResource(R.string.files_scanning),
                onCancel = if (state.phase == ToolPhase.Scanning) {
                    { onIntent(WhatsAppCleanerIntent.BackPressed) }
                } else {
                    null
                },
            )
            state.cleaning?.let { CleaningOverlay(it) }
        }
    }
    ConfirmDialogHost(
        spec = state.confirm,
        onConfirm = { onIntent(WhatsAppCleanerIntent.CleanConfirmed) },
        onDismiss = { onIntent(WhatsAppCleanerIntent.CleanDismissed) },
    )
    ConfirmDialogHost(
        spec = state.stopConfirm,
        onConfirm = { onIntent(WhatsAppCleanerIntent.CancelCleanConfirmed) },
        onDismiss = { onIntent(WhatsAppCleanerIntent.CancelCleanDismissed) },
    )
    state.expandedBucket?.let { bucket ->
        BucketDetailSheet(
            bucket = bucket,
            onDismiss = { onIntent(WhatsAppCleanerIntent.BucketExpanded(null)) },
        )
    }
}

@Composable
private fun WhatsAppBody(
    state: WhatsAppCleanerState,
    onIntent: (WhatsAppCleanerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        !state.whatsAppInstalled ->
            EmptyState(stringResource(R.string.whatsapp_not_installed), modifier)

        // An empty scan shows an empty state. It does NOT arm a countdown and press Clean (§6.5).
        state.isEmptyResult -> EmptyState(stringResource(R.string.whatsapp_empty), modifier)
        else -> BucketGrid(state, onIntent, modifier)
    }
}

@Composable
private fun BucketGrid(
    state: WhatsAppCleanerState,
    onIntent: (WhatsAppCleanerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(BucketColumns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = ScreenGutter,
            end = ScreenGutter,
            bottom = PageSpacing.listBottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        items(
            items = state.buckets,
            key = { it.id },
            contentType = { BucketTileType },
        ) { bucket ->
            BucketTile(
                bucket = bucket,
                selected = bucket.id in state.selected,
                onIntent = onIntent,
            )
        }
    }
}

private const val BucketColumns = 3
private const val BucketTileType = "whatsapp.tile"
