package com.pion.phonecleaner.feature.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlinx.collections.immutable.ImmutableList

/**
 * The grid's two shapes. `Card` is the competitor's `beverdou` include, `Icon` its `Thrichil` tile —
 * the same data, a different item composable (`docs/screens/11-home.md` §1.3).
 *
 * These four types are the tile vocabulary of [HomeState.sections]. They live beside `HomeContract.kt`
 * rather than in it only to keep both files under the 200-line rule
 * (`.claude/rules/development-rules.md`); State, Intent and Effect stay in the contract file.
 */
@Immutable
enum class TileStyle { Card, Icon }

@Immutable
sealed interface TileBadge {
    data object None : TileBadge
    data object Dot : TileBadge

    /** The composable renders `"99+"` above 99 — the cap is a rendering rule, not a datum. */
    data class Count(val value: Int) : TileBadge
}

/**
 * One entry point in the grid.
 *
 * [pill] stays `null` from the ViewModel. The one live pill on this screen is the network rate, and
 * delta 14 puts formatting in the composable so the locale is the **render** locale: `FeatureGrid`
 * reads `HomeState.downloadBytesPerSecond` and formats it with `rememberByteFormat()`. The field is
 * kept because the appendix's contract declares it and a future non-rate pill belongs here.
 */
@Immutable
data class HomeTile(
    val feature: FeatureId,
    val badge: TileBadge = TileBadge.None,
    val pill: String? = null,
    /**
     * The tile is drawn and cannot be entered — `FeatureAvailability.comingSoon`, read once in
     * [HomeSections.build] rather than at render, so what the grid draws and what the reducer allows
     * are the same fact and a test can pin both.
     */
    val isComingSoon: Boolean = false,
)

/** A titled run of tiles. `titleRes == null` is the untitled card grid at the top of the page. */
@Immutable
data class HomeSection(
    @param:StringRes @get:StringRes val titleRes: Int?,
    val style: TileStyle,
    val tiles: ImmutableList<HomeTile>,
)
