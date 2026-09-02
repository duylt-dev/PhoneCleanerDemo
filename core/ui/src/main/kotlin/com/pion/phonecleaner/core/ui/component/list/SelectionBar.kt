package com.pion.phonecleaner.core.ui.component.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.R
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The count-and-act bar under a selectable list. It replaces five re-declared
 * `allChooseAppSize`/`alldeleteSize` Activity **field pairs** — five copies of one running total,
 * each maintained by hand next to its own adapter (system-architecture §4.7).
 *
 * It derives nothing. [selectedCount] and [selectedBytes] come off the screen's `State`, where
 * selection lives as a `Set<Id>` beside the list rather than as a flag on each item (LLM.md §8): a
 * mutated item is `equals` its predecessor inside the old list, so no diff can see it.
 *
 * The size is rendered through `rememberByteFormat()`, which takes its locale from
 * `LocalConfiguration` — a ViewModel must not build user-facing copy (`docs/screens/21` §6.1).
 *
 * @param actionLabel the primary action's own word. `null` gives "Delete". The four clusters that use
 *   this bar name the action differently — Clean, Continue, Delete, Uninstall — and a shared component
 *   that names one screen is shareable with exactly one screen (`docs/screens/21` §4.6 C3).
 */
@Composable
fun SelectionBar(
    selectedCount: Int,
    selectedBytes: Long,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
) {
    val bytes = rememberByteFormat()
    Surface(
        modifier = modifier.fillMaxWidth().height(SelectionBarHeight),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = SelectionBarElevation,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            TextButton(onClick = onSelectAll) { Text(stringResource(R.string.action_select_all)) }
            Column(Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.selection_count, selectedCount, selectedCount),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = bytes.size(selectedBytes).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onDelete, enabled = selectedCount > 0) {
                Text(actionLabel ?: stringResource(R.string.action_delete))
            }
        }
    }
}

/**
 * A **position**, not a gap (MVI §11). 72 dp is what `PageSpacing.listBottom` and
 * `PageSpacing.snackbarLift` were measured against, so the three move together or not at all.
 */
val SelectionBarHeight = 72.dp

private val SelectionBarElevation = 3.dp
