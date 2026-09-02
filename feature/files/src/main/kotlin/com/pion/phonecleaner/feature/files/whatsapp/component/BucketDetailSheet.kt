package com.pion.phonecleaner.feature.files.whatsapp.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.component.dialog.AppBottomSheet
import com.pion.phonecleaner.core.ui.component.tile.LabelValueRow
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.domain.model.file.WhatsAppBucket

/**
 * The drill-down the competitor does not have (§6.5). It renders `bucket.files`, which the ViewModel
 * already holds for the delete — no second query, no second walk.
 *
 * Read-only: the selection is per bucket, and a per-file selection would be a second selection model
 * for the same delete. The sheet exists so a user can SEE what a bucket contains before removing it.
 */
@Composable
internal fun BucketDetailSheet(
    bucket: WhatsAppBucket,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Text(
            text = stringResource(bucket.id.labelRes()),
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SheetListMaxHeight),
        ) {
            items(
                items = bucket.files,
                key = { it.id },
                contentType = { DetailRowType },
            ) { file ->
                LabelValueRow(
                    icon = null,
                    label = file.name,
                    value = bytes.size(file.sizeBytes).toString(),
                )
            }
        }
    }
}

private val SheetListMaxHeight = 360.dp
private const val DetailRowType = "whatsapp.detail"
