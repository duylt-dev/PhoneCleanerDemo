package com.pion.phonecleaner.core.ui.component.tile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The suggestion card. Replaces `buffrran` (system-architecture §4.7).
 *
 * Its region in the competitor is **not a list** — one to four fixed cards — so there is no adapter,
 * no key and no `contentType` here (`docs/screens/21` §2.1, last row). A caller emits between one and
 * four of these into its own `Column`.
 *
 * What it recommends comes from `FeatureUsageRepository.recommend()`, whose staleness rule is still a
 * product decision (`docs/screens/21` §8 item 3, chapter 10 U10). Nothing about that decision is
 * encoded here: this component renders four strings and a lambda.
 *
 * **No call site as of 2026-09-03.** Its only caller was the clean-result suggestion list, removed by
 * owner decision: this build exercises each feature on its own and ships no cross-feature convenience
 * layer. The component is kept because `docs/system-architecture.md` §4.7 and `docs/screens/21` §2.1
 * table it against the competitor's `buffrran`, and deleting it would leave those rows pointing at
 * nothing. Do not read its presence as evidence that something on screen draws it.
 */
@Composable
fun RecommendationCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ctaLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // decorative: `title` is the accessible name
                modifier = Modifier.size(CardIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(Modifier.weight(1f).padding(horizontal = Spacing.lg)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClick) { Text(ctaLabel) }
        }
    }
}

/** A **position**, not a gap (MVI §11): 40 dp, the size at which a leading glyph balances two lines. */
private val CardIconSize = 40.dp
