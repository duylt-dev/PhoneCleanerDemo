package com.pion.phonecleaner.core.ui.component.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import kotlinx.collections.immutable.ImmutableList

/**
 * What `ud.d`'s two lookups — position to view type, view type to layout id — were for. They exist
 * only because a `RecyclerView` cannot ask an item what it is. A sealed interface can be asked
 * (`docs/screens/21` §2.2).
 *
 * It lives in `:core:ui`, **not** `:domain`: it exists to give a `LazyColumn` two `contentType`s,
 * which is a rendering concern (system-architecture §4.1).
 */
@Immutable
sealed interface MediaEntry {
    val key: String

    data class Header(val yearMonth: String, val totalBytes: Long) : MediaEntry {
        override val key get() = "h:$yearMonth"
    }

    data class File(
        val path: String,
        val name: String,
        val sizeKb: Float,
        val takenAtMillis: Long,
        val yearMonth: String,
    ) : MediaEntry {
        override val key get() = path
    }
}

/**
 * One keyed list over an `ImmutableList`, replacing `kd.h`, `ud.d` and the two photo grids.
 *
 * All four of the competitor's list facts disappear at once here: there is no view to cache, no
 * `View` tag to carry an `Int` position and be silently overwritten, no single `OnClickListener`
 * installed on every row, and no `notifyDataSetChanged()` — the new immutable list *is* the change
 * notification (`docs/screens/21` §2).
 *
 * [selected] is a `Set` of paths held on `State` beside the list, never a flag on the item: the
 * competitor flips the bound item's flag in place at `kd.h.x():159` and is then obliged to rebind
 * every visible row (§2.1 L5).
 *
 * [onIntent] is passed **as-is**, never wrapped per item — a per-row lambda allocated inside `items {}`
 * defeats the skip for every row on every emission (LLM.md §8).
 *
 * UNKNOWN — no source names the `Intent` a shared row fires. `docs/screens/21` §2.2 writes
 * `MediaRow(entry, entry.path in selected, onIntent)` with `onIntent: (UiIntent) -> Unit`, but
 * `:core:ui` cannot construct any cluster's `Intent` and `:core:mvi`'s `FileToolIntent.ToggleItem` is
 * an interface with no concrete arm. Looked in `docs/screens/21` §2.2/§2.4,
 * `docs/screens/13-photo-and-media.md` §1.3 and `docs/screens/14-file-tools-and-app-manager.md` §2 —
 * each names its own screen's intent, none names one for the shared component. So [toggleIntent]
 * hoists the mapping to the call site: one lambda per list, not one per row, which is the property
 * LLM.md §8 actually cares about. Declaring an intent type here instead would put a presentation type
 * in a shared module that `LLM.md` §4 has no row for.
 */
@Composable
fun MediaList(
    entries: ImmutableList<MediaEntry>,
    selected: Set<String>,
    onIntent: (UiIntent) -> Unit,
    toggleIntent: (MediaEntry.File) -> UiIntent,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = ScreenGutter,
        end = ScreenGutter,
        bottom = PageSpacing.listBottom,
    ),
) {
    val bytes = rememberByteFormat()
    LazyColumn(modifier = modifier, contentPadding = contentPadding) {
        items(
            items = entries,
            key = { it.key },
            contentType = { if (it is MediaEntry.Header) "header" else "file" },
        ) { entry ->
            when (entry) {
                is MediaEntry.Header -> SectionHeader(
                    title = "${entry.yearMonth}  ·  ${bytes.size(entry.totalBytes)}",
                )

                is MediaEntry.File -> MediaRow(
                    entry = entry,
                    isSelected = entry.path in selected,
                    label = bytes.size((entry.sizeKb * BytesPerKb).toLong()).toString(),
                    onIntent = onIntent,
                    toggleIntent = toggleIntent,
                )
            }
        }
    }
}

private const val BytesPerKb = 1024f

@Composable
private fun MediaRow(
    entry: MediaEntry.File,
    isSelected: Boolean,
    label: String,
    onIntent: (UiIntent) -> Unit,
    toggleIntent: (MediaEntry.File) -> UiIntent,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = isSelected,
                role = Role.Checkbox,
                onValueChange = { onIntent(toggleIntent(entry)) },
            )
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isSelected, onCheckedChange = null)
        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
