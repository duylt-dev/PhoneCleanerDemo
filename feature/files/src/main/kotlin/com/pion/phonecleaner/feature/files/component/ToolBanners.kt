package com.pion.phonecleaner.feature.files.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.file.ScanCoverage
import com.pion.phonecleaner.feature.files.R

/**
 * "Here is what this scan could NOT look at."
 *
 * `docs/system-architecture.md` §8.4 makes this mandatory for the default (MediaStore + SAF) branch:
 * the branch cannot see everything and must say so. The competitor's file-reputation scan silently
 * degrades to installed packages only and reports nothing about it.
 *
 * [onGrantMore] opens a SAF tree picker. It never asks for `MANAGE_EXTERNAL_STORAGE`, which is never
 * assumed grantable (owner decision, §8.1).
 */
@Composable
internal fun CoverageBanner(
    coverage: ScanCoverage,
    onGrantMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!coverage.isPartial) return
    ToolBanner(
        message = stringResource(R.string.files_coverage_partial, coverage.surfaceCount),
        actionLabel = stringResource(R.string.files_coverage_grant_more),
        onAction = onGrantMore,
        modifier = modifier,
    )
}

/**
 * The scan ran out of budget and published what it had.
 *
 * The competitor's audio engine arms a 10 s watchdog that stops the cursor loop mid-way and **reports
 * success**; its duplicate finder does the same at 4 s. Truncation that is not surfaced is a silent
 * wrong answer, which is worse than a slow one.
 */
@Composable
internal fun TruncationBanner(visible: Boolean, modifier: Modifier = Modifier) {
    if (!visible) return
    ToolBanner(
        message = stringResource(R.string.files_scan_truncated),
        modifier = modifier,
    )
}

@Composable
internal fun ToolBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
