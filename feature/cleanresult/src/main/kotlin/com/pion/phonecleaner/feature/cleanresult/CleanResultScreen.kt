package com.pion.phonecleaner.feature.cleanresult

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.feature.cleanresult.component.CountingHeadline

/**
 * One screen for fifteen features (`docs/screens/14-file-tools-and-app-manager.md` §8).
 *
 * The back arrow is present and works. The competitor's equivalent has no arrow at all and blocks
 * the system key with a toast, because its own navigation cannot survive being left early.
 *
 * **It offers no onward feature.** The headline, the lifetime total and Done are the whole page —
 * the suggestion list this screen used to draw was removed by owner decision (2026-09-03), so there
 * is nothing here that can name another feature. `ResultPhase` survives that removal because the
 * count-up still needs it: `isRevealed` is what stops [CountingHeadline] animating.
 */
@Composable
internal fun CleanResultScreen(
    state: CleanResultState,
    onIntent: (CleanResultIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.clean_result_title),
                onBack = { onIntent(CleanResultIntent.BackPressed) },
            )
            // A `Column` that scrolls, not a `LazyColumn`: with the suggestion list gone this page
            // holds ONE block of fixed height. Lazy machinery for a single item buys nothing and
            // costs a key and a `contentType` that no longer identify anything.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = PageSpacing.listBottom),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ResultHeadline(state, onIntent)
            }
            Button(
                onClick = { onIntent(CleanResultIntent.DonePressed) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter, vertical = Spacing.md),
            ) {
                Text(stringResource(R.string.clean_result_done))
            }
        }
    }
}

@Composable
private fun ResultHeadline(state: CleanResultState, onIntent: (CleanResultIntent) -> Unit) {
    val itemLine = pluralStringResource(
        R.plurals.clean_result_item_count,
        state.itemCount,
        state.itemCount,
    )
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = stringResource(state.outcomeHeadlineRes()),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (state.showsBytes) {
            CountingHeadline(
                targetBytes = state.freedBytes,
                counting = !state.isRevealed,
                onFinished = { onIntent(CleanResultIntent.CountingAnimationFinished) },
                caption = if (state.itemCount > 0) itemLine else null,
            )
        } else {
            // A run that freed nothing still reports honestly, and reports it without an animation
            // to sit through — the photo-privacy strip removes location data and frees zero bytes.
            NoBytesHeadline(state, itemLine, onIntent)
        }
        // The second line a MovedToTrash run owes: the bytes just counted up are not gone yet
        // (plan 260908-0801-trash-bin, Phase 07) — CleanupLedger was not written for this run.
        if (state.summary.outcome == CleanupOutcome.MovedToTrash) {
            Text(
                text = stringResource(R.string.clean_result_trash_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = ScreenGutter),
            )
        }
        if (state.lifetimeFreedBytes > 0L) {
            Text(
                text = stringResource(
                    R.string.clean_result_lifetime,
                    rememberByteFormat().size(state.lifetimeFreedBytes).toString(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = ScreenGutter),
            )
        }
    }
}

/**
 * There is no count-up to run, so the reveal Intent is raised once, from the composable, exactly as
 * the counting branch raises it — otherwise the screen would stay in `Counting` forever and never
 * show its suggestions.
 */
@Composable
private fun NoBytesHeadline(
    state: CleanResultState,
    itemLine: String,
    onIntent: (CleanResultIntent) -> Unit,
) {
    if (state.itemCount > 0) {
        Text(
            text = itemLine,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = Spacing.lg),
        )
    }
    androidx.compose.runtime.LaunchedEffect(state.isRevealed) {
        if (!state.isRevealed) onIntent(CleanResultIntent.CountingAnimationFinished)
    }
}

private fun CleanResultState.outcomeHeadlineRes(): Int = when (summary.outcome) {
    CleanupOutcome.Cleaned -> R.string.clean_result_headline_cleaned
    CleanupOutcome.NothingFound -> R.string.clean_result_headline_nothing_found
    CleanupOutcome.ThreatsRemoved -> R.string.clean_result_headline_threats_removed
    CleanupOutcome.DataCleared -> R.string.clean_result_headline_data_cleared
    CleanupOutcome.ItemsCleared -> R.string.clean_result_headline_items_cleared
    CleanupOutcome.MovedToTrash -> R.string.clean_result_headline_moved_to_trash
}
