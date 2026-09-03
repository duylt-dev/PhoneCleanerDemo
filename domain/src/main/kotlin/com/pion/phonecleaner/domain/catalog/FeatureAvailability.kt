package com.pion.phonecleaner.domain.catalog

import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * Which entry points this release can actually carry out, and which are still waiting on a decision.
 *
 * A pure `object` and **not in Koin**, for the reason `FeatureCatalog` is not: a dependency-free
 * lookup that holds no state and touches no platform gains nothing from injection
 * (`LLM.md` §4 "a static lookup table with no state and no platform", §6.3).
 *
 * ## Why an availability table exists at all
 *
 * Four of the twenty entry points reach an implementation that PENDING OWNER DECISIONS 1, 2 and 3
 * deliberately left inert, and every one of them is on the home page. Without this table each one
 * renders as a working tile that opens a screen with nothing on it, which is the failure the wording
 * rule exists to prevent: the app claims a capability it does not have. Naming them here says so once,
 * in the layer that knows what a feature *is*, rather than four times in four screens.
 *
 * ## The list, and what closes each row
 *
 * | Feature | Decision | What it reaches today | What closes it |
 * |---|---|---|---|
 * | [FeatureId.JunkClean] | 1 | `EmptyJunkRuleCatalog` — the system-cache and app-residual passes evaluate zero candidates | the catalogue fork: `AssetJunkRuleCatalog` or `KotlinJunkRuleCatalog` |
 * | [FeatureId.WhatsAppCleaner] | 1 | `EmptyWhatsAppRoots` — six buckets, no paths, always the empty state | the same fork: a roots table bound in `filesDataModule` |
 * | [FeatureId.NetworkTest] | 2 | `UnconfiguredSpeedTestRepository` — no socket, no bytes, no figure | a procured byte source, or the decision to delete the two speed-test screens |
 * | [FeatureId.RunningApps] | 3 | a screen that renders, but only once the user finds `PACKAGE_USAGE_STATS` in Settings unaided | whether the app asks for that grant |
 *
 * Removing a row is the whole change: the tile unlocks, the hero button re-enables, the exit offer and
 * the clean-result suggestions may name it again, and nothing else moves.
 *
 * **This is not the same statement as `HomeSections.notOnHome`.** That set removes a tile from the
 * page; this one keeps the tile drawn and locked. A feature that vanishes teaches the user it was
 * never there; a feature that is present and says it is not ready teaches them to come back.
 */
object FeatureAvailability {

    /**
     * The entry points that are drawn but cannot be entered. Read by the home reducer before it
     * raises a navigation, and by the usage ledger before it recommends one.
     */
    val comingSoon: ImmutableSet<FeatureId> = persistentSetOf(
        FeatureId.JunkClean,
        FeatureId.WhatsAppCleaner,
        FeatureId.NetworkTest,
        FeatureId.RunningApps,
    )

    /**
     * Whether [feature] may be opened.
     *
     * The check belongs in a **reducer**, never in a router or a click handler alone: the home page
     * reaches `NavigateToFeature` from four directions — a tile tap, a deep link, the exit offer and a
     * resolved permission — and only one of them passes through a composable that could disable
     * itself.
     */
    fun isAvailable(feature: FeatureId): Boolean = feature !in comingSoon
}
