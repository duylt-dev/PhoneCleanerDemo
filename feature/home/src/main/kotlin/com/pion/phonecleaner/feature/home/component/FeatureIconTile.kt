package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.catalog.FeatureDescriptors
import com.pion.phonecleaner.core.ui.theme.Pill
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.home.HomeIntent
import com.pion.phonecleaner.feature.home.HomeTile
import com.pion.phonecleaner.feature.home.R
import com.pion.phonecleaner.feature.home.TileBadge

/**
 * One icon tile — the competitor's `Thrichil` custom `ViewGroup`, which is an icon, a label and two
 * badge overlays, and is therefore a `Column` (`docs/screens/11-home.md` §1.3).
 *
 * Same data as [FeatureCard], different shape: no card, no aspect ratio, no value line. The two
 * sections that use it are the eight "save space" entry points and the three privacy ones.
 *
 * Having no value line is why a locked tile grows a caption here and reuses the value slot there:
 * [FeatureAvailability]'s "Coming soon" has to be readable on both shapes, and inventing a second
 * value line on this one would be a layout no source draws. The click is disabled and the glyph
 * drops to the muted content colour — a token, not a call-site alpha (MVI §11).
 */
@Composable
internal fun FeatureIconTile(
    tile: HomeTile,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val descriptor = FeatureDescriptors.of(tile.feature)
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = !tile.isComingSoon) {
                onIntent(HomeIntent.FeatureTapped(tile.feature))
            }
            .padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box {
            Icon(
                imageVector = descriptor.icon,
                contentDescription = null, // decorative: the label below is the accessible name
                modifier = Modifier.size(TileIconSize),
                tint = if (tile.isComingSoon) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            TileBadgeOverlay(tile.badge, Modifier.align(Alignment.TopEnd))
        }
        Text(
            text = stringResource(descriptor.titleRes),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (tile.isComingSoon) {
            Text(
                text = stringResource(R.string.home_tile_coming_soon),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The competitor's `entirais` dot and `decorpectu` count, which are two overlays on one tile. */
@Composable
private fun TileBadgeOverlay(badge: TileBadge, modifier: Modifier = Modifier) {
    val count = badgeText(badge)
    when {
        count != null -> Surface(
            modifier = modifier,
            shape = Pill,
            color = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ) {
            Text(
                text = count,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
                style = MaterialTheme.typography.labelSmall,
            )
        }

        badge is TileBadge.Dot ->
            AttentionDot(stringResource(R.string.home_badge_attention), modifier)

        else -> Unit
    }
}

/** A **position**, not a gap (MVI §11): 28 dp, the size at which the glyph reads as a symbol. */
private val TileIconSize = 28.dp
