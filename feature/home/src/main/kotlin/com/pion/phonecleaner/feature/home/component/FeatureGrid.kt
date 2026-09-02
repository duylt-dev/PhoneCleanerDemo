package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.ByteFormat
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenGutter
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.feature.home.HomeIntent
import com.pion.phonecleaner.feature.home.HomeSection
import com.pion.phonecleaner.feature.home.HomeTile
import com.pion.phonecleaner.feature.home.R
import com.pion.phonecleaner.feature.home.TileStyle

/**
 * One run of tiles, as rows of three.
 *
 * **Not a `LazyVerticalGrid`.** Eight fixed items nested inside the page's `LazyColumn` would need a
 * fixed height, which is the one thing a tile grid must not have
 * (`docs/screens/11-home.md` §1.3). `chunked(COLUMNS)` into `Row`s of `Modifier.weight(1f)`, with a
 * spacer for the ragged last row, is the same arrangement with no measurement conflict — and it
 * replaces eleven hand-repeated tile blocks in 983 lines of XML (delta 20).
 *
 * [onIntent] is passed down as-is. The per-tile lambda is built *inside* [FeatureCard] /
 * [FeatureIconTile], whose parameters are all stable, so an unchanged tile skips and allocates
 * nothing on the next emission — which matters here because a 1 Hz rate pill sits in this grid
 * (`LLM.md` §8).
 */
@Composable
internal fun FeatureGrid(
    section: HomeSection,
    downloadBytesPerSecond: Long?,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Resolved once for the whole run rather than inside the loop: `rememberByteFormat` keys on the
    // render locale, and one instance per grid is one slot instead of one per tile.
    val format = rememberByteFormat()
    Column(
        modifier = modifier.screenGutter().fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        section.tiles.chunked(COLUMNS).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                row.forEach { tile ->
                    when (section.style) {
                        TileStyle.Card -> FeatureCard(
                            tile = tile,
                            value = tileValue(tile, downloadBytesPerSecond, format),
                            onIntent = onIntent,
                            modifier = Modifier.weight(1f),
                        )

                        TileStyle.Icon -> FeatureIconTile(
                            tile = tile,
                            onIntent = onIntent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                // The competitor's invisible ninth tile, which is a spacer wearing a tile's costume.
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * The one live figure on this screen.
 *
 * `null` renders `"--"` and never a zero: `TrafficStats.getTotalRxBytes()` returns `-1` on devices
 * without the counters, and the competitor's `-1` reaches the card as a figure (delta 4). The
 * placeholder is a string resource rather than a literal so a translator owns it.
 *
 * The rate is device-wide and since boot — that is what the API measures, and there is no cheaper
 * whole-device figure — so the tile's own title is what says whose traffic it is.
 */
@Composable
private fun tileValue(
    tile: HomeTile,
    downloadBytesPerSecond: Long?,
    format: ByteFormat,
): String? = when {
    tile.feature != FeatureId.NetworkTraffic -> tile.pill
    downloadBytesPerSecond == null -> stringResource(R.string.home_value_unknown)
    else -> format.rate(downloadBytesPerSecond).toString()
}

/** Three across, the competitor's `lagcat` grid and its `x1()` tile rows. */
private const val COLUMNS = 3
