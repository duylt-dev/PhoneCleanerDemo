package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.catalog.FeatureDescriptors
import com.pion.phonecleaner.core.ui.component.tile.QuickToolTile
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.home.HomeIntent
import com.pion.phonecleaner.feature.home.HomeTile
import com.pion.phonecleaner.feature.home.R
import com.pion.phonecleaner.feature.home.TileBadge

/**
 * One card in the top grid — the competitor's `beverdou`, included eight times.
 *
 * The card itself is `:core:ui`'s [QuickToolTile], which already carries `Fakilabl`'s
 * `aspectRatio(0.9434f)`; this file is the mapping from a [HomeTile] onto it, and nothing else.
 *
 * The title is resolved by `stringResource` at render, never captured into a `String` once per
 * process the way `ae.i2` does — that is what leaves every feature name in the previous language
 * after the in-app picker changes it.
 */
@Composable
internal fun FeatureCard(
    tile: HomeTile,
    value: String?,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val descriptor = FeatureDescriptors.of(tile.feature)
    Box(modifier) {
        QuickToolTile(
            icon = descriptor.icon,
            label = stringResource(descriptor.titleRes),
            onClick = { onIntent(HomeIntent.FeatureTapped(tile.feature)) },
            modifier = Modifier.fillMaxWidth(),
            badge = badgeText(tile.badge),
            value = value,
        )
        if (tile.badge is TileBadge.Dot) {
            AttentionDot(
                description = stringResource(R.string.home_badge_attention),
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
            )
        }
    }
}

/**
 * `null` for the two badges that carry no number: [QuickToolTile] draws nothing, and a
 * [TileBadge.Dot] is drawn beside it instead.
 *
 * The `"99+"` cap is a **rendering** rule and lives here rather than on the model, so the count on
 * state stays the count that was measured.
 */
@Composable
internal fun badgeText(badge: TileBadge): String? = when (badge) {
    TileBadge.None, TileBadge.Dot -> null
    is TileBadge.Count -> when {
        badge.value <= 0 -> null
        badge.value > BADGE_CAP -> stringResource(R.string.home_badge_overflow)
        else -> badge.value.toString()
    }
}

private const val BADGE_CAP = 99
