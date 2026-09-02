package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.feature.FeatureId

/**
 * The one analytics port. `Analytics`, `AnalyticsTracker` and `AnalyticsClient` all lost to this
 * name; **nine cluster designs declared it under four names**, and it is bound exactly once, in
 * `coreDataModule` (`docs/system-architecture.md` §4.1, §5.4).
 *
 * [track] does not suspend. Every call site fires it from inside a reducer arm — for example the
 * `FeatureTapped` arm of home (`docs/screens/11-home.md:229`) — and a reducer neither suspends nor
 * waits on a network. The implementation hands the event to the `named("appScope")` scope; a failure
 * is logged, never surfaced.
 */
interface AnalyticsRepository {
    fun track(event: AnalyticsEvent)
}

/**
 * Every analytics event, in one file, so the numeric ids cannot be maintained by hand in two places —
 * which is what produced the competitor's `100525`/`100526` transposition
 * (`docs/system-architecture.md` §4.1, `LLM.md` §5).
 *
 * **A cluster that needs a new event adds an arm HERE**, with its verified id, and never declares a
 * local event type: a sealed interface admits implementations only in this module.
 *
 * Only ids we verified are carried. `101807` has no recovered call site and `100508` / `100519` /
 * `100524` do not exist in the APK at all (`docs/screens/11-home.md:509`), so no arm claims them.
 */
sealed interface AnalyticsEvent {

    /**
     * A feature was opened. The wire id is [FeatureId.analyticsId] — verified, and on the enum, so a
     * destination and its event can never be edited apart.
     */
    data class FeatureOpened(val feature: FeatureId) : AnalyticsEvent

    /**
     * The exit dialog offered a feature (`docs/screens/11-home.md:229`).
     * UNKNOWN — no competitor id corresponds to it; the wire id is the implementation's to assign.
     */
    data class ExitOfferShown(val feature: FeatureId) : AnalyticsEvent

    /**
     * A clean was requested, with what the user had selected
     * (`docs/screens/12-junk-cleaning.md:488` — an event the competitor does not have at all).
     */
    data class CleanRequested(val selectedBytes: Long, val selectedCount: Int) : AnalyticsEvent

    // The junk cluster's three events. Names and shapes from docs/screens/12-junk-cleaning.md §3.2
    // and §5.5. The competitor has NO analytics id for any of them, so none is proposed here —
    // inventing a wire id would look checked.
    data class JunkScanFinished(val totalBytes: Long, val durationMs: Long, val itemCount: Int) : AnalyticsEvent

    data class JunkScanCancelled(val atPass: Int) : AnalyticsEvent

    data class JunkCleanFinished(
        val freedBytes: Long,
        val deletedCount: Int,
        val failedCount: Int,
        val durationMs: Long,
    ) : AnalyticsEvent
}
