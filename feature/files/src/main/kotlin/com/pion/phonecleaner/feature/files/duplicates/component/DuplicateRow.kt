package com.pion.phonecleaner.feature.files.duplicates.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.component.FileRow
import com.pion.phonecleaner.feature.files.duplicates.DuplicatesIntent

/**
 * One member of a group. `onIntent` arrives as-is and the two `() -> Unit` callbacks `FileRow` takes
 * are `remember`ed on the row's own id, so no new instance is allocated per recomposition and the
 * row stays skippable (`LLM.md` §8).
 *
 * The kept copy is **labelled**, not hidden: the competitor writes `isLatest` onto the model inside
 * its hashing loop and the user can never tell which copy survives (§2.2).
 */
@Composable
internal fun DuplicateRow(
    file: ScannedFile,
    selected: Boolean,
    isKept: Boolean,
    onIntent: (DuplicatesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val id = file.id
    val onToggle = remember(id, onIntent) { { onIntent(DuplicatesIntent.RowToggled(id)) } }
    val onOpen = remember(id, onIntent) { { onIntent(DuplicatesIntent.RowTapped(id)) } }
    Column(modifier) {
        FileRow(file = file, selected = selected, onToggle = onToggle, onOpen = onOpen)
        if (isKept) {
            Text(
                text = stringResource(R.string.duplicates_newest),
                modifier = Modifier.padding(start = ScreenGutter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
