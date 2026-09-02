package com.pion.phonecleaner.feature.files.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.files.R

/**
 * The three controls `video` and `audio` share. One copy, so the sort labels and the permission copy
 * cannot drift between two screens that are otherwise the same machine (§4).
 */
@Composable
internal fun MediaSortChips(
    sort: MediaSort,
    onSelect: (MediaSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // The three arms are written out rather than looped: `MediaSort.entries` would pair each
        // label with a position instead of with its own constant, and a reorder would then silently
        // relabel every chip.
        SortChip(MediaSort.NewestFirst, sort, R.string.media_sort_newest, onSelect)
        SortChip(MediaSort.LargestFirst, sort, R.string.media_sort_largest, onSelect)
        SortChip(MediaSort.Name, sort, R.string.media_sort_name, onSelect)
    }
}

@Composable
private fun SortChip(
    value: MediaSort,
    current: MediaSort,
    labelRes: Int,
    onSelect: (MediaSort) -> Unit,
) {
    // `remember`ed on the constant, not allocated per recomposition — three chips, three instances
    // for the life of the screen (`LLM.md` §8).
    val onClick = remember(value, onSelect) { { onSelect(value) } }
    FilterChip(
        selected = value == current,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
    )
}

/**
 * Android 14's `READ_MEDIA_VISUAL_USER_SELECTED`: the user granted *some* items, and that is a normal
 * outcome rather than a failure (`docs/system-architecture.md` §8.3). The banner offers the way to
 * widen it; nothing here re-raises the system dialog on its own.
 */
@Composable
internal fun PartialAccessBanner(
    visible: Boolean,
    onGrantMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    ToolBanner(
        message = stringResource(R.string.media_partial_access),
        actionLabel = stringResource(R.string.media_permission_grant),
        onAction = onGrantMore,
        modifier = modifier,
    )
}

/**
 * What the screen renders instead of a list when the grant is `Denied`.
 *
 * The competitor has no such state: it checks the permission *outside* the screen and never starts
 * the Activity, so a denial is a screen that does not open and says nothing.
 */
@Composable
internal fun MediaPermissionState(onGrant: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        message = stringResource(R.string.media_permission_denied),
        modifier = modifier,
        action = { Button(onClick = onGrant) { Text(stringResource(R.string.media_permission_grant)) } },
    )
}
