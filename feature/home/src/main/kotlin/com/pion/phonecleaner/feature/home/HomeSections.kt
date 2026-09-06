package com.pion.phonecleaner.feature.home

import com.pion.phonecleaner.domain.catalog.FeatureAvailability
import com.pion.phonecleaner.domain.catalog.FeatureCatalog
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The home grid's arrangement: which entry point sits in which run, in which order.
 *
 * **Why it is here and not in `:domain/catalog/`.** `LLM.md` §4 sends a static lookup table to
 * `:domain/catalog/`, and `FeatureCatalog` is that table — it answers *what a feature requires*.
 * This one answers *where a tile is drawn on one screen*, which is an arrangement, and `:domain` is
 * the one module that may not know a screen exists (`LLM.md` §2). It is a plain `object` and not in
 * Koin for the same reason `FeatureCatalog` is not: no state, no platform (`LLM.md` §6.3).
 *
 * Order is verbatim from the competitor's own wiring — the eight cards of `A1()` and the eleven tiles
 * of `x1()` (`docs/reverse-engineering/11-home.md` §4). Adding a feature is one enum constant plus one
 * row here, which is what replaces the competitor's "edit four places" (delta 20).
 */
internal object HomeSections {

    /**
     * Every permission any entry point on this page needs, read off the adjudicated catalogue rather
     * than a hand-written list so it cannot drift from the gate in the reducer. It is a fact about
     * this arrangement — *these* twenty tiles — which is why it sits beside the layout.
     *
     * UNKNOWN — the competitor's own header predicate is `od.q0.b()` and no source states which
     * permissions it tests (`docs/screens/11-home.md` §1.1 records only the call site). The union of
     * the catalogue is the conservative reading: the warning appears when something a tile needs is
     * missing, and never for a permission nothing on this page asks for.
     */
    val permissionsBehindTiles: Set<AppPermission> =
        FeatureId.entries.flatMapTo(mutableSetOf()) { FeatureCatalog.requires(it) }

    /** The hero button, not a tile: the competitor's `horgende`, analytics `100505`. */
    val heroFeature: FeatureId = FeatureId.JunkClean

    /** `A1()` — the untitled 3-column card grid at the top of the page. */
    private val cardFeatures = persistentListOf(
        FeatureId.Antivirus,
        FeatureId.NotificationCleaner,
        FeatureId.RunningApps,
        FeatureId.AppManager,
        FeatureId.BatteryInfo,
        FeatureId.DeviceStatus,
        FeatureId.NetworkTraffic,
        FeatureId.NetworkTest,
    )

    /**
     * `x1()` rows 1–3 — the "save space" icon tiles.
     *
     * [FeatureId.BlurryPhotos] is the one entry here with no counterpart in `x1()`: the competitor
     * has no sharpness heuristic at all (`docs/reverse-engineering/13-photo-and-media.md:544`). It is
     * placed next to [FeatureId.SimilarPhotos] because the two answer the same question — *which of
     * my photos is not worth keeping* — and a reader who opened one is the reader looking for the
     * other.
     */
    private val saveSpaceFeatures = persistentListOf(
        FeatureId.BigFiles,
        FeatureId.DuplicateFiles,
        FeatureId.WhatsAppCleaner,
        FeatureId.SimilarPhotos,
        FeatureId.BlurryPhotos,
        FeatureId.PhotoCompressor,
        FeatureId.ImageManager,
        FeatureId.VideoManager,
        FeatureId.AudioManager,
    )

    /** `x1()` row 4 — the privacy and access tiles. */
    private val securityFeatures = persistentListOf(
        FeatureId.AppLock,
        FeatureId.PermissionManager,
        FeatureId.PhotoPrivacy,
    )

    /**
     * Features that exist but deliberately have no tile on this page.
     *
     * Still **empty**, and it is not where a deferred feature goes. PENDING OWNER DECISIONS 1, 2 and
     * 3 leave four entry points inert, and the owner's answer was to keep every one of them drawn and
     * locked rather than removed — `FeatureAvailability.comingSoon` carries that list and [toTiles]
     * reads it. This set stays for the other case: a decision that says a feature is not on this page
     * at all. Its row would move out of the run above and into here, and the completeness check below
     * would keep passing — which is the point of having the escape hatch rather than a `check` that
     * must be weakened to make a decision.
     */
    private val notOnHome: Set<FeatureId> = emptySet()

    init {
        // The guarantee behind the appendix's "a catalogue fake with 500 entries still renders the
        // known set" test: the layout is a fixed table, so nothing a catalogue grows can reach it.
        // Every FeatureId is either laid out exactly once or listed in `notOnHome`, deliberately.
        val laidOut = cardFeatures + saveSpaceFeatures + securityFeatures + heroFeature
        val accounted = laidOut.toSet() + notOnHome
        check(accounted == FeatureId.entries.toSet() && laidOut.size == laidOut.toSet().size) {
            "HomeSections does not account for every feature exactly once: " +
                "${FeatureId.entries.toSet() - accounted} unplaced, ${laidOut.size} laid out"
        }
    }

    /**
     * The three runs, rebuilt whole on every badge change (`LLM.md` §8: a state collection is replaced,
     * never mutated).
     *
     * UNKNOWN — [badges] is empty today. Every source the appendix names is owned by another cluster
     * and has **no `:domain` interface yet**: `CleanStatsRepository` (`backgroundModule`) for the
     * safety-check dot, `NotificationCleanerRepository` (`notificationDataModule`) for the hidden
     * count, `ScanBadgeRepository` (`deviceDataModule`) for the Running Apps dot the competitor keys
     * on `flux_running_apps_last_scan_ymd` (`docs/screens/11-home.md` §4.3 item 2,
     * `docs/reverse-engineering/11-home.md` §4). None is in `:domain/repository/`, and this cluster
     * does not own that package; the parameter exists so adding one is a call-site change, and an
     * invented count would look checked.
     */
    fun build(badges: Map<FeatureId, TileBadge> = emptyMap()): ImmutableList<HomeSection> =
        persistentListOf(
            HomeSection(null, TileStyle.Card, cardFeatures.toTiles(badges)),
            HomeSection(R.string.home_section_save_space, TileStyle.Icon, saveSpaceFeatures.toTiles(badges)),
            HomeSection(R.string.home_section_security, TileStyle.Icon, securityFeatures.toTiles(badges)),
        )

    /**
     * `isComingSoon` is resolved **here**, not in the tile composable, for the reason every other
     * fact on this page is: the grid draws state, and a test that reads `HomeState.sections` must be
     * able to see a locked tile without composing anything.
     */
    private fun List<FeatureId>.toTiles(badges: Map<FeatureId, TileBadge>): ImmutableList<HomeTile> =
        map {
            HomeTile(
                feature = it,
                badge = badges[it] ?: TileBadge.None,
                isComingSoon = !FeatureAvailability.isAvailable(it),
            )
        }.toImmutableList()
}
