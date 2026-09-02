package com.pion.phonecleaner.feature.files.video.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.file.ScannedFile

/**
 * One video in the grid.
 *
 * The play badge is drawn **conditionally**, on the tile the user is not currently selecting; the
 * competitor's is unconditionally visible in a grid that only ever holds videos, which makes it
 * decoration that costs a draw pass per cell.
 *
 * There is no thumbnail here yet: `:feature:files` has no image loader on its classpath for media
 * URIs, and inventing one is not this screen's decision to make.
 * UNKNOWN — no appendix states which loader draws a *media* thumbnail. Looked for:
 * `docs/screens/14-file-tools-and-app-manager.md` §3.3 (which says "Coil `AsyncImage` with an `error`
 * placeholder" for §1.3's file rows but names no model type for a `MediaStore` video), and
 * `core/ui/icon/AppIconLoader.kt`, which resolves *package* icons only. The size and name are real
 * data and are shown instead of a fabricated frame.
 */
@Composable
internal fun VideoCell(
    file: ScannedFile,
    selected: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    Column(modifier.clickable(onClick = onOpen).padding(Spacing.xs)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!selected) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null, // decorative: the file name is the accessible name
                    modifier = Modifier.align(Alignment.Center).size(CellBadgeSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Text(
            text = file.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = bytes.size(file.sizeBytes).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A **position**, not a gap (MVI §11): the centred badge of a square media cell. */
private val CellBadgeSize = 32.dp
