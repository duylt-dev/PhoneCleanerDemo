package com.pion.phonecleaner.feature.antivirus.result.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberRelativeTimestamp
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.security.RiskLevel
import com.pion.phonecleaner.feature.antivirus.R

/**
 * The count and when it was measured (`docs/screens/15-antivirus.md` §2.3).
 *
 * **A plural resource, not `"n issue found"`** — the competitor concatenates an English singular
 * whatever the count and whatever the locale (§2.5).
 *
 * **The tint comes from [level]**, which is `state.headlineLevel`: the competitor hard-codes its red
 * even for a list whose every row is a low score. `RiskLevel.Clean` therefore reads as ordinary
 * text, because a list of low-score rows is not an alarm.
 */
@Composable
internal fun ResultHeadline(
    count: Int,
    level: RiskLevel,
    scannedAtEpochMs: Long?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter)
            .padding(top = PageSpacing.headerToContent, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = pluralStringResource(R.plurals.antivirus_result_headline, count, count),
            style = MaterialTheme.typography.headlineSmall,
            // Only the top level tints the headline, and it tints it with the theme's error
            // role rather than a literal: `Elevated` is a list to look at, not an alarm.
            color = if (level == RiskLevel.High) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        if (scannedAtEpochMs != null) {
            Text(
                text = stringResource(
                    R.string.antivirus_result_scanned_at,
                    rememberRelativeTimestamp(scannedAtEpochMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
