package com.pion.phonecleaner.feature.antivirus.result.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.security.RiskLevel
import com.pion.phonecleaner.feature.antivirus.R

/**
 * **One composable over [RiskLevel], so the cut-point exists exactly once** — in `RiskLevel.of`
 * (`docs/screens/15-antivirus.md` §2.3). The competitor scatters the same two numbers over an
 * adapter and a list filter, a `>= 6` filter and a `< 8` badge switch, so a row can be filtered in
 * by one rule and drawn by the other (§0.1).
 *
 * The words are ours and describe *a finding to review*: no label here states that anything was
 * detected or is dangerous, which is the wording ban at its sharpest (`LLM.md` §5).
 */
@Composable
internal fun RiskBadge(risk: RiskLevel, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = risk.container(),
        contentColor = risk.onContainer(),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(risk.labelRes()),
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** The category the vendor's own table gives, rendered where the competitor renders nothing (§2.5). */
@Composable
internal fun CategoryChip(category: String, modifier: Modifier = Modifier) {
    if (category.isBlank()) return
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = category,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun RiskLevel.labelRes(): Int = when (this) {
    RiskLevel.High -> R.string.antivirus_risk_high
    RiskLevel.Elevated -> R.string.antivirus_risk_elevated
    RiskLevel.Clean -> R.string.antivirus_risk_clean
}

@Composable
private fun RiskLevel.container(): Color = when (this) {
    RiskLevel.High -> MaterialTheme.colorScheme.errorContainer
    RiskLevel.Elevated -> MaterialTheme.colorScheme.tertiaryContainer
    RiskLevel.Clean -> MaterialTheme.colorScheme.secondaryContainer
}

@Composable
private fun RiskLevel.onContainer(): Color = when (this) {
    RiskLevel.High -> MaterialTheme.colorScheme.onErrorContainer
    RiskLevel.Elevated -> MaterialTheme.colorScheme.onTertiaryContainer
    RiskLevel.Clean -> MaterialTheme.colorScheme.onSecondaryContainer
}
