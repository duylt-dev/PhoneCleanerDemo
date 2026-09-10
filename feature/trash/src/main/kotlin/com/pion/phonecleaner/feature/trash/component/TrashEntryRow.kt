package com.pion.phonecleaner.feature.trash.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.catalog.FeatureDescriptors
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashEntryKind
import com.pion.phonecleaner.domain.model.trash.TrashEntryType
import com.pion.phonecleaner.feature.trash.ExpiryLabel
import com.pion.phonecleaner.feature.trash.R
import com.pion.phonecleaner.feature.trash.TrashIntent

/**
 * One row. `onIntent` is passed down as-is and this composable builds its own
 * `TrashIntent.EntryToggled(entry.id)` inline, both in the row's `clickable` and in the checkbox —
 * allocated once per row recomposition, not once per list emission, because `key = { it.id }` on the
 * caller's `LazyColumn` keeps every other row from recomposing at all (`LLM.md` §8).
 *
 * [label] is resolved by the caller (`TrashEntry.expiryLabel(state.now)`, in `TrashReducers.kt`) so
 * this composable does no date arithmetic of its own — it only turns the chosen id into text.
 */
@Composable
internal fun TrashEntryRow(
    entry: TrashEntry,
    label: ExpiryLabel,
    isSelected: Boolean,
    onIntent: (TrashIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onIntent(TrashIntent.EntryToggled(entry.id)) }
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = entry.icon(),
            contentDescription = null, // decorative: `entry.displayName` is the accessible name
            modifier = Modifier.size(RowIconSize),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entrySubtitle(entry, bytes = bytes.size(entry.sizeBytes).toString()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = expiryText(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onIntent(TrashIntent.EntryToggled(entry.id)) },
        )
    }
}

/** "4.2 MB · 12 files · From Junk cleaner" — the file-count segment only for a directory entry. */
@Composable
private fun entrySubtitle(entry: TrashEntry, bytes: String): String {
    val source = stringResource(
        R.string.trash_entry_source,
        stringResource(FeatureDescriptors.of(entry.source).titleRes),
    )
    val fileCount = if (entry.kind == TrashEntryKind.Directory) {
        pluralStringResource(R.plurals.trash_entry_file_count, entry.fileCount, entry.fileCount)
    } else {
        null
    }
    return listOfNotNull(bytes, fileCount, source).joinToString(SubtitleSeparator)
}

@Composable
private fun expiryText(label: ExpiryLabel): String = when (label) {
    is ExpiryLabel.DaysLeft -> pluralStringResource(R.plurals.trash_expires_in_days, label.days, label.days)
    ExpiryLabel.Today -> stringResource(R.string.trash_expires_today)
}

private fun TrashEntry.icon() = when {
    type == TrashEntryType.Zip -> Icons.Filled.Inventory2
    kind == TrashEntryKind.Directory -> Icons.Filled.Folder
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private const val SubtitleSeparator = " · "

/** A **position**, not a gap (MVI §11): the leading glyph of a two-line list row. */
private val RowIconSize = 40.dp
