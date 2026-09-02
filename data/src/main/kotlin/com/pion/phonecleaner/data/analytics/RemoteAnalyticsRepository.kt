package com.pion.phonecleaner.data.analytics

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository

/**
 * The analytics seam. Declared once, in `coreDataModule` — **nine** cluster designs declared it, under
 * four different names (`docs/system-architecture.md` §5.1, §5.4). It replaces `qd.b` + `pd.a`.
 *
 * ### There is no backend, and this must not pretend there is one
 *
 * *"There is no backend and none is in scope"* (`docs/system-architecture.md` §1). The port of the
 * competitor's ad, remote-config and push facades is **the boundary only**; the internals are out of
 * scope. So this implementation records an event and stops. It does not queue for a transport that
 * does not exist, it does not open a socket, and it does not silently drop events into a buffer that
 * a later reader would mistake for a delivery record.
 *
 * **The seam is `AnalyticsRepository` itself**, which is a `:domain` interface every ViewModel already
 * takes by constructor. When a transport is chosen, it is written here and no call site changes. The
 * name keeps the `Remote` prefix because §5.4 and `docs/screens/21` §6.3 both name this class, and a
 * rename would be the tenth name for one thing.
 *
 * `track` is deliberately **not** `suspend`: every call site fires it from inside a reducer arm, and a
 * reducer does not suspend.
 */
internal class RemoteAnalyticsRepository(
    private val logger: AppLogger,
) : AnalyticsRepository {

    /**
     * Never throws. Analytics is bookkeeping the user did not ask for, so it fails silently to the
     * log and never to a snackbar (§7.6). The log line carries an event name and at most a feature id
     * — never a package list, a file path list or a usage history (§7.7).
     */
    override fun track(event: AnalyticsEvent) {
        val id = event.wireId()
        logger.d { "analytics ${event.name()} id=${id ?: UNASSIGNED_ID}" }
    }

    /**
     * The verified wire id, or null where none exists.
     *
     * Only `FeatureOpened` has one, and it is **read off `FeatureId.analyticsId`** rather than
     * restated here — that is the entire reason the id lives on the enum: the competitor keeps its
     * two numbering systems in two files and has already transposed a pair of them
     * (`docs/screens/21-shared-models-and-ui.md` §5.1).
     *
     * UNKNOWN — ids for `ExitOfferShown` and `CleanRequested`. `docs/screens/11-home.md:509` records
     * that `101807` has no recovered call site and that `100508` / `100519` / `100524` **do not exist
     * in the APK**. Looked there, in `docs/reverse-engineering/02` §4's event table, and in each
     * cluster appendix's analytics row. A fabricated id is worse than a blank, because it looks
     * checked; a null here is visible in the log line as `unassigned`.
     */
    private fun AnalyticsEvent.wireId(): String? = when (this) {
        is AnalyticsEvent.FeatureOpened -> feature.analyticsId
        is AnalyticsEvent.ExitOfferShown -> null
        is AnalyticsEvent.CleanRequested -> null
        // UNKNOWN — no wire id exists for any of the three junk events. The competitor emits none:
        // docs/screens/12-junk-cleaning.md §3.2 and §5.5 name the events, and neither the appendix nor
        // docs/reverse-engineering/02 §4's event table carries an id for them.
        is AnalyticsEvent.JunkScanFinished -> null
        is AnalyticsEvent.JunkScanCancelled -> null
        is AnalyticsEvent.JunkCleanFinished -> null
    }

    private fun AnalyticsEvent.name(): String = when (this) {
        is AnalyticsEvent.FeatureOpened -> "feature_opened:${feature.name}"
        is AnalyticsEvent.ExitOfferShown -> "exit_offer_shown:${feature.name}"
        is AnalyticsEvent.CleanRequested -> "clean_requested:$selectedCount"
        is AnalyticsEvent.JunkScanFinished -> "junk_scan_finished:$itemCount"
        is AnalyticsEvent.JunkScanCancelled -> "junk_scan_cancelled:$atPass"
        is AnalyticsEvent.JunkCleanFinished -> "junk_clean_finished:$deletedCount"
    }

    private companion object {
        const val UNASSIGNED_ID = "unassigned"
    }
}
