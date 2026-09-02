package com.pion.phonecleaner.feature.photo.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.isGroupFullySelected
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * The grouped photo lane `similar`, `compressor` and `privacy` all draw
 * (`docs/screens/13-photo-and-media.md` §1.3, §3.3, §5.3).
 *
 * **A header is a full-width span, not a model row.** That is the whole fix for the competitor's
 * `viewType: 0|1` model, whose adapter returns headers inside the selected list so they reach the
 * engine with `path == ""` (§5.1).
 *
 * Every lane is keyed by the `MediaStore` `_id` and carries a `contentType`, so a tap recomposes one
 * cell. Both callbacks take an id and are hoisted **once** by the caller: a lambda written inside
 * `items {}` is a new instance per recomposition and defeats the skip for the whole lane
 * (`LLM.md` §8).
 */
internal fun LazyGridScope.photoGroupItems(
    groups: ImmutableList<PhotoGroup>,
    selectedIds: ImmutableSet<PhotoId>,
    onToggleGroup: (String) -> Unit,
    onTogglePhoto: (PhotoId) -> Unit,
    markKeptPhoto: Boolean = false,
    headerActionLabel: String? = null,
    onHeaderAction: ((String) -> Unit)? = null,
    onOpenPhoto: ((PhotoId) -> Unit)? = null,
) {
    groups.forEach { group ->
        item(
            key = "header:${group.key}",
            span = { GridItemSpan(maxLineSpan) },
            contentType = PHOTO_HEADER_CONTENT_TYPE,
        ) {
            PhotoGroupHeader(
                group = group,
                fullySelected = selectedIds.isGroupFullySelected(group),
                onToggleGroup = onToggleGroup,
                actionLabel = headerActionLabel,
                onAction = onHeaderAction,
            )
        }
        items(
            items = group.photos,
            key = { it.id.value },
            contentType = { PHOTO_CELL_CONTENT_TYPE },
        ) { photo ->
            PhotoCell(
                photo = photo,
                selected = photo.id in selectedIds,
                onToggle = onTogglePhoto,
                // "Keep the newest" is the default the competitor also uses. What it cannot do is
                // let the user move it — the badge marks the group's own `keptId` (§1.4).
                keep = markKeptPhoto && photo.id == group.keptId,
                // Hoisted by the caller and passed straight through, exactly like `onTogglePhoto`.
                onOpen = onOpenPhoto,
            )
        }
    }
}

@Composable
private fun PhotoGroupHeader(
    group: PhotoGroup,
    fullySelected: Boolean,
    onToggleGroup: (String) -> Unit,
    actionLabel: String?,
    onAction: ((String) -> Unit)?,
) {
    val bytes = rememberByteFormat()
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Checkbox(checked = fullySelected, onCheckedChange = { onToggleGroup(group.key) })
        Text(
            text = group.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = bytes.size(group.totalBytes).toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = { onAction(group.key) }) { Text(actionLabel) }
        }
    }
}
