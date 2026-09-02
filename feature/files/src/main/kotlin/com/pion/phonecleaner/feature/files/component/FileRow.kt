package com.pion.phonecleaner.feature.files.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.ScannedFile

/**
 * One file, in the three list tools that show files rather than groups or tiles.
 *
 * Shared by `bigfiles`, `duplicates` and `audio` — a cluster-level component, which is the only place
 * a composable used by more than one screen of one cluster can live without being promoted to
 * `:core:ui` (`LLM.md` §4).
 *
 * **[selected] is passed in, not read off the item.** The competitor mutates `isSelected` on its row
 * model and calls `notifyDataSetChanged()` on every tap; a mutated item is `equals` its predecessor
 * inside the old list, so in Compose no diff could see it either (`LLM.md` §8).
 *
 * The size is formatted here, by `rememberByteFormat()`, whose locale comes from
 * `LocalConfiguration` — a ViewModel never builds user-facing copy.
 */
@Composable
internal fun FileRow(
    file: ScannedFile,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = file.kind.icon(),
            contentDescription = null, // decorative: the file name is the accessible name
            modifier = Modifier.size(RowIconSize),
        )
        Column(Modifier.weight(1f).padding(horizontal = Spacing.md)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = bytes.size(file.sizeBytes).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = selected, onCheckedChange = { onToggle() })
    }
}

private fun FileKind.icon(): ImageVector = when (this) {
    FileKind.Image -> Icons.Filled.Image
    FileKind.Video -> Icons.Filled.Videocam
    FileKind.Audio -> Icons.Filled.Audiotrack
    FileKind.Apk -> Icons.Filled.Android
    FileKind.Other -> Icons.Filled.InsertDriveFile
}

/** A **position**, not a gap (MVI §11): the leading glyph of a two-line list row. */
private val RowIconSize = 40.dp
