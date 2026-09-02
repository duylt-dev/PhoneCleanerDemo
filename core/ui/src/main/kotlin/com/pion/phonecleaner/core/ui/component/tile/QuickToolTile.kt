package com.pion.phonecleaner.core.ui.component.tile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.theme.Pill
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * The home tile. Replaces `beverdou`, included eight times, all inside home
 * (system-architecture §4.7).
 *
 * [value] is nullable and **that is the port of a defect**: the competitor draws the literal `"--"`
 * as a placeholder, which a translator has to handle and a screen reader reads aloud. A value that is
 * not known yet is `null`, and nothing draws.
 *
 * The tile's shape carries `Modifier.aspectRatio(0.9434f)` — `Fakilabl`, one of the six competitor
 * custom views, deleted in favour of a modifier. Its XML attribute is width ÷ height and Compose uses
 * the same convention, so **the number ports unchanged** (`docs/screens/21` §4.4).
 *
 * [icon] is an `ImageVector` rather than the `@DrawableRes iconRes` §4.7's table names — see
 * `FeatureDescriptor`'s KDoc for why: this repository has none of the competitor's icon assets, and
 * inventing drawable files would be inventing content.
 */
@Composable
fun QuickToolTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    value: String? = null,
) {
    Card(onClick = onClick, modifier = modifier.aspectRatio(TileAspectRatio)) {
        Box(Modifier.fillMaxWidth().padding(Spacing.md)) {
            Column(
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterVertically),
            ) {
                Icon(icon, contentDescription = null, Modifier.size(TileIconSize))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (badge != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd),
                    shape = Pill,
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

/** `Fakilabl`'s own default, ported unchanged: width ÷ height (`docs/screens/21` §4.4). */
private const val TileAspectRatio = 0.9434f

/** A **position**, not a gap (MVI §11): the tile glyph reads as a symbol at 28 dp and as art above it. */
private val TileIconSize = 28.dp
