package com.pion.phonecleaner.feature.photo.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.R

/**
 * One square thumbnail with its selection box. Shared by `similar`, `compressor`, `privacy` and
 * `albumdetail`, which is why it sits in the cluster-level `component/` package (`LLM.md` §3.7).
 *
 * **[onToggle] takes the id, not a pre-bound lambda.** A grid hoists one `(PhotoId) -> Unit` above
 * its `items {}` and passes that same instance to every cell; a `{ onIntent(Toggled(p.id)) }`
 * written inside `items {}` is a new instance on every recomposition and defeats the skip for every
 * cell in the lane (`LLM.md` §8).
 *
 * [onOpen] is optional and takes the id for the same reason [onToggle] does. Which group and which
 * index that id sits at is the **reducer's** lookup, not the cell's: a `(groupKey, index)` callback
 * could only be built as a per-item lambda inside `items {}` (`LLM.md` §8).
 *
 * The thumbnail is [Photo.contentUri] — never a `_data` path. The competitor wraps each image load
 * in a `runCatching` so a broken path cannot crash the bind; Coil returns a failed request instead.
 */
@Composable
fun PhotoCell(
    photo: Photo,
    selected: Boolean,
    onToggle: (PhotoId) -> Unit,
    modifier: Modifier = Modifier,
    keep: Boolean = false,
    onOpen: ((PhotoId) -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                onValueChange = { onToggle(photo.id) },
            ),
    ) {
        AsyncImage(
            model = photo.contentUri,
            contentDescription = photo.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Checkbox(
            checked = selected,
            onCheckedChange = null, // the whole cell is the toggle target
            modifier = Modifier.align(Alignment.TopEnd),
        )
        // The open affordance is a separate target, not a long-press: the whole cell is already
        // the selection toggle. `similar` and `blurry` are the screens that have somewhere to open
        // to — both hand their scan to the shared pager through a `PhotoSessionStore` (§1.3).
        if (onOpen != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Spacing.xs)
                    .clickable { onOpen(photo.id) },
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.photo_open_preview),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        if (keep) {
            Surface(
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.xs),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.photo_keep_badge),
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** Three columns, one constant — the four grids in this cluster must agree (`MVI` §11). */
const val PHOTO_GRID_COLUMNS: Int = 3

/** `contentType` for a photo lane. Never a literal at four call sites. */
const val PHOTO_CELL_CONTENT_TYPE: String = "photoCell"

/** `contentType` for a full-span group header. */
const val PHOTO_HEADER_CONTENT_TYPE: String = "photoGroupHeader"
