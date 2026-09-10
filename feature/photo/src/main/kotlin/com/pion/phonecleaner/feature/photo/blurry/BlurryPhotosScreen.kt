package com.pion.phonecleaner.feature.photo.blurry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.list.SelectionBar
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.domain.model.photo.BlurTier
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.blurry.component.BlurryPhotosDialogs
import com.pion.phonecleaner.feature.photo.blurry.component.BlurryPhotosNotices
import com.pion.phonecleaner.feature.photo.component.PHOTO_GRID_COLUMNS
import com.pion.phonecleaner.feature.photo.component.PhotoCompletionPanel
import com.pion.phonecleaner.feature.photo.component.PhotoScanPanel
import com.pion.phonecleaner.feature.photo.component.photoGroupItems
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The blurry-photo grid: one section per blur tier, blurriest first.
 *
 * Every callback is hoisted **once**, above the lane: a lambda written inside `items {}` is a new
 * instance on every recomposition and defeats the skip for every cell in the grid (`LLM.md` §8).
 *
 * `markKeptPhoto` is **not** passed to [photoGroupItems]. The similar grid badges each group's
 * opener "Keeping" because that photo survives its own default; a blur tier has no survivor — every
 * row in it is pre-selected — so the badge would tell the reader a photo is safe that is in fact
 * ticked for deletion.
 */
@Composable
internal fun BlurryPhotosScreen(
    state: BlurryPhotosState,
    onIntent: (BlurryPhotosIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onTogglePhoto: (PhotoId) -> Unit = { id -> onIntent(BlurryPhotosIntent.PhotoToggled(id)) }
    val onToggleTier: (String) -> Unit = { key -> onIntent(BlurryPhotosIntent.TierToggled(key)) }
    val onOpenPhoto: (PhotoId) -> Unit = { id -> onIntent(BlurryPhotosIntent.PhotoOpened(id)) }
    val groups = rememberLocalisedTiers(state.groups)
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.photo_blurry_title),
                onBack = { onIntent(BlurryPhotosIntent.BackPressed) },
            )
            TotalsHeader(bytes = state.totalBytes)
            when (state.phase) {
                ToolPhase.Scanning -> PhotoScanPanel(
                    label = stringResource(R.string.photo_blurry_scoring),
                    done = state.scored,
                    total = state.toScore,
                )

                ToolPhase.Completing -> PhotoCompletionPanel(
                    onFinished = { onIntent(BlurryPhotosIntent.CompletionAnimationFinished) },
                )

                else -> Unit
            }
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    onRetry = { onIntent(BlurryPhotosIntent.ScreenStarted) },
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )
            }
            BlurryPhotosNotices(state)
            if (state.showEmptyState) {
                EmptyState(message = stringResource(R.string.photo_blurry_empty))
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
                        groups = groups,
                        selectedIds = state.selectedIds,
                        onToggleGroup = onToggleTier,
                        onTogglePhoto = onTogglePhoto,
                        onOpenPhoto = onOpenPhoto,
                    )
                }
            }
            SelectionBar(
                // `canDelete`, not `selectedCount`: on re-entry the surviving session store
                // repopulates the full pre-selection while a fresh scan is still running, so a bar
                // driven by the count alone would draw an ENABLED Delete button for the whole scan
                // and then swallow every tap. A disabled control that lies is the failure MVI §3
                // means by "guard re-entrancy in the reducer, not in the UI".
                enabled = state.canDelete,
                selectedCount = state.selectedCount,
                selectedBytes = state.selectedBytes,
                onSelectAll = { onIntent(BlurryPhotosIntent.SelectAllToggled) },
                onDelete = { onIntent(BlurryPhotosIntent.DeletePressed) },
            )
        }
    }
    BlurryPhotosDialogs(state, onIntent)
}

/**
 * Swaps each group's machine-shaped `label` for the localised tier name.
 *
 * **This is where the tier becomes user copy, and it is the only place it can be.** `PhotoGroup.label`
 * is contractually a machine-shaped string because `:domain` has no `Resources` and must not format
 * user copy (MVI §5) — the scanner therefore writes `BlurTier.name`, and the header composable
 * renders `group.label` verbatim. Resolving it here, at render, is also what makes the in-app
 * language picker work: the competitor resolves feature names to `String`s at object initialisation
 * (`java/ae/i2.java:315-415`) in an app with 17 locales, so switching language leaves every name
 * stale until the process restarts.
 *
 * `remember`ed on the groups and both labels: it rebuilds only when the scan result or the locale
 * changes, and the list is at most two entries long either way. A group whose key matches no tier is
 * left alone rather than dropped — an unrenderable header is a bug, an invisible row is data loss.
 */
@Composable
private fun rememberLocalisedTiers(groups: ImmutableList<PhotoGroup>): ImmutableList<PhotoGroup> {
    val veryBlurry = stringResource(R.string.photo_blurry_tier_very)
    val slightlyBlurry = stringResource(R.string.photo_blurry_tier_slight)
    return remember(groups, veryBlurry, slightlyBlurry) {
        groups.map { group ->
            when (group.key) {
                BlurTier.VeryBlurry.name -> group.copy(label = veryBlurry)
                BlurTier.SlightlyBlurry.name -> group.copy(label = slightlyBlurry)
                else -> group
            }
        }.toImmutableList()
    }
}

/** What the whole result weighs — formatted for the locale, never a hand-built string. */
@Composable
private fun TotalsHeader(bytes: Long) {
    val format = rememberByteFormat()
    Text(
        text = format.size(bytes).toString(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        style = MaterialTheme.typography.headlineSmall,
    )
}
