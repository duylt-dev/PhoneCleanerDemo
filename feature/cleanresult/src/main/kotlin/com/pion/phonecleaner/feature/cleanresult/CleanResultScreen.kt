package com.pion.phonecleaner.feature.cleanresult

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.pion.phonecleaner.core.ui.catalog.FeatureDescriptors
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SectionHeader
import com.pion.phonecleaner.core.ui.component.tile.RecommendationCard
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.feature.cleanresult.component.CountingHeadline

/**
 * One screen for fifteen features (`docs/screens/14-file-tools-and-app-manager.md` §8).
 *
 * The back arrow is present and works. The competitor's equivalent has no arrow at all and blocks
 * the system key with a toast, because its own navigation cannot survive being left early.
 */
@Composable
internal fun CleanResultScreen(
    state: CleanResultState,
    onIntent: (CleanResultIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.clean_result_title),
                onBack = { onIntent(CleanResultIntent.BackPressed) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = HeadlineKey, contentType = HeadlineType) {
                    ResultHeadline(state, onIntent)
                }
                if (state.isRevealed && state.suggestions.isNotEmpty()) {
                    item(key = SuggestionsHeaderKey, contentType = SectionType) {
                        SectionHeader(
                            title = stringResource(R.string.clean_result_suggestions_title),
                            modifier = Modifier.padding(horizontal = ScreenGutter),
                        )
                    }
                    items(
                        items = state.suggestions,
                        // The `FeatureId` IS the identity; nothing here is keyed by position.
                        key = { it.name },
                        contentType = { SuggestionType },
                    ) { feature ->
                        val descriptor = FeatureDescriptors.of(feature)
                        RecommendationCard(
                            icon = descriptor.resultIcon,
                            title = stringResource(descriptor.titleRes),
                            subtitle = stringResource(descriptor.descriptionRes),
                            ctaLabel = stringResource(descriptor.exitCtaRes),
                            // `onIntent` is passed through a stable lambda that captures only the
                            // feature; a `RecommendationCard` takes `() -> Unit`, so this is the one
                            // shape it admits.
                            onClick = { onIntent(CleanResultIntent.SuggestionTapped(feature)) },
                            modifier = Modifier.padding(horizontal = ScreenGutter),
                        )
                    }
                }
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
}

private const val HeadlineKey = "cleanResultHeadline"
private const val SuggestionsHeaderKey = "cleanResultSuggestionsHeader"
private const val HeadlineType = "cleanResultHeadline"
private const val SectionType = "cleanResultSection"
private const val SuggestionType = "cleanResultSuggestion"
