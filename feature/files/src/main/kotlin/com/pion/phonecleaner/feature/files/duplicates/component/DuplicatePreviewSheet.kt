package com.pion.phonecleaner.feature.files.duplicates.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppBottomSheet
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.feature.files.R

/**
 * The "View" sheet (§2.3). The competitor's equivalent is a `Dialog` field on the Activity; here the
 * open state is an **id** on `DuplicatesState` and the sheet is a function of it.
 *
 * The path is shown because two copies of one file differ only by where they are — the whole reason
 * a user opens this sheet before deleting one of them.
 */
@Composable
internal fun DuplicatePreviewSheet(
    file: ScannedFile,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(file.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = file.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(bytes.size(file.sizeBytes).toString(), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onOpen) { Text(stringResource(R.string.duplicates_preview_open)) }
        }
    }
}
