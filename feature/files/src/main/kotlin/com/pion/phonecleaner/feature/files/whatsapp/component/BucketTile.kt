package com.pion.phonecleaner.feature.files.whatsapp.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.file.WhatsAppBucket
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.whatsapp.WhatsAppCleanerIntent

/**
 * One group (`docs/screens/14-file-tools-and-app-manager.md` §6.3). A tap selects it; a long press
 * opens the detail sheet over `bucket.files`, which the ViewModel already holds — the competitor
 * shows six totals and gives no way to see or spare a single file (§6.5).
 *
 * `onIntent` arrives as-is and both callbacks are `remember`ed on the bucket id (`LLM.md` §8).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BucketTile(
    bucket: WhatsAppBucket,
    selected: Boolean,
    onIntent: (WhatsAppCleanerIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bytes = rememberByteFormat()
    val id = bucket.id
    val onToggle = remember(id, onIntent) { { onIntent(WhatsAppCleanerIntent.BucketToggled(id)) } }
    val onExpand = remember(id, onIntent) { { onIntent(WhatsAppCleanerIntent.BucketExpanded(id)) } }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onLongClick = onExpand, onClick = onToggle),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = stringResource(id.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                text = bytes.size(bucket.totalBytes).toString(),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                // Through a <plurals>: the competitor appends an English "s" in all 17 locales.
                text = pluralStringResource(
                    R.plurals.whatsapp_bucket_count,
                    bucket.fileCount,
                    bucket.fileCount,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
