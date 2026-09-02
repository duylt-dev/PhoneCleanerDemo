package com.pion.phonecleaner.feature.junk.junkreview.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.junk.JunkItem
import com.pion.phonecleaner.feature.junk.junkreview.JunkReviewIntent

/**
 * One item row.
 *
 * `yc.a` is a nested `RecyclerView` **per category row**, with its own `LinearLayoutManager` and
 * `itemAnimator = null` to hide the symptoms of the nested scroll container (Delta R12). Here the
 * rows are emitted straight into the one `LazyColumn`.
 *
 * The label comes from [JunkItem.label], built by the mapper from the item's origin. The competitor
 * shows `File(path).name` with the path as a fallback, so the user reads `cache`, `temp`,
 * `external-sd` and `.goproduct` and cannot tell what any of them is, or which app it belonged to
 * (Delta R4). The path is still shown, as the secondary line, because it is the truth about what
 * will be deleted.
 */
@Composable
internal fun JunkItemRow(
    item: JunkItem,
    selected: Boolean,
    onIntent: (JunkReviewIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onIntent(JunkReviewIntent.ItemCheckTapped(item.path)) }
            .padding(horizontal = ScreenGutter, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onIntent(JunkReviewIntent.ItemCheckTapped(item.path)) },
        )
        Column(Modifier.weight(1f).padding(horizontal = Spacing.sm)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }
        Text(
            text = rememberByteFormat().size(item.sizeBytes).toString(),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
