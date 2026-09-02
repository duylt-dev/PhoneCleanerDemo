package com.pion.phonecleaner.feature.files.appmanager.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.appmanager.AppSort
import com.pion.phonecleaner.feature.files.appmanager.AppSortKey
import com.pion.phonecleaner.feature.files.appmanager.UninstallProgress

/** Was three `TextView`s and a `SpannableString` built inside a `runCatching` (§5.3). */
@Composable
internal fun TotalsHeader(bytes: Long, count: Int, modifier: Modifier = Modifier) {
    val format = rememberByteFormat()
    Text(
        text = stringResource(R.string.app_manager_totals, format.size(bytes).toString(), count),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        style = MaterialTheme.typography.titleMedium,
    )
}

/**
 * Three chips over six competitor comparators. *Last used* is **disabled without the grant**, since
 * every row would read `0` — the screen itself stays open (§5.5).
 */
@Composable
internal fun SortChipRow(
    sort: AppSort,
    lastUsedEnabled: Boolean,
    onSelect: (AppSortKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SortChip(AppSortKey.Size, sort, R.string.app_manager_sort_size, true, onSelect)
        SortChip(AppSortKey.InstallDate, sort, R.string.app_manager_sort_installed, true, onSelect)
        SortChip(
            AppSortKey.LastUsed,
            sort,
            R.string.app_manager_sort_last_used,
            lastUsedEnabled,
            onSelect,
        )
    }
}

@Composable
private fun SortChip(
    value: AppSortKey,
    current: AppSort,
    labelRes: Int,
    enabled: Boolean,
    onSelect: (AppSortKey) -> Unit,
) {
    val onClick = remember(value, onSelect) { { onSelect(value) } }
    FilterChip(
        selected = value == current.key,
        onClick = onClick,
        label = { Text(stringResource(labelRes)) },
        enabled = enabled,
    )
}

/**
 * "3 of 7" — the state the competitor does not render at all. Its uninstall phase changes nothing on
 * screen, and the stacked system dialogs are the only feedback the user gets (§5.3).
 */
@Composable
internal fun UninstallProgressBar(progress: UninstallProgress, modifier: Modifier = Modifier) {
    val total = progress.total
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            text = stringResource(R.string.app_manager_uninstall_progress, progress.done, total),
            style = MaterialTheme.typography.labelLarge,
        )
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else progress.done.toFloat() / total },
            modifier = Modifier.weight(1f),
        )
    }
}
