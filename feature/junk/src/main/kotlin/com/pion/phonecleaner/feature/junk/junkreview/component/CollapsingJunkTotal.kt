package com.pion.phonecleaner.feature.junk.junkreview.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.feature.junk.R

/**
 * The total, in the collapsing app bar.
 *
 * `isHeaderCollapsed` is **not state**: it is read from the bar's own
 * `TopAppBarScrollBehavior.state.collapsedFraction` and passed in here (§4.1's folding table). The
 * competitor keeps it as an Activity field updated from a scroll listener, which is a second copy of
 * something the scroll already knows.
 */
@Composable
internal fun CollapsingJunkTotal(
    totalBytes: Long,
    collapsedFraction: Float,
    modifier: Modifier = Modifier,
) {
    val size = rememberByteFormat().size(totalBytes)
    if (collapsedFraction > CollapsedThreshold) {
        Text(
            text = stringResource(R.string.junk_review_total_collapsed, size.toString()),
            modifier = modifier,
            style = MaterialTheme.typography.titleLarge,
        )
        return
    }
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(text = size.toString(), style = MaterialTheme.typography.headlineLarge)
        Text(
            text = stringResource(R.string.junk_review_total_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Past this, the bar is short enough that only the one-line form fits. */
private const val CollapsedThreshold = 0.5f
