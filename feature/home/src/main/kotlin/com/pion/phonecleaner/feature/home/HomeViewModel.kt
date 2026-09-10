package com.pion.phonecleaner.feature.home

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.catalog.FeatureAvailability
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.JunkRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.StorageInfoRepository
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The hub. `onIntent` is its only public method, and it holds **no job fields**: `isJunkEstimating`
 * in the reducer plus a bounded wait replaces `estimateJob`, and `tickerJob` has nothing to cancel
 * while `NetworkTrafficRepository` does not exist. Every coroutine is anonymous under
 * `viewModelScope` through `launchSafely` / `collectSafely` — a sub-job held as a field is the shape
 * one missed cancellation leaks for the life of the process (delta 3).
 */
class HomeViewModel(
    savedStateHandle: SavedStateHandle,
    storageInfo: StorageInfoRepository,
    cleanupLedger: CleanupLedger,
    private val junk: JunkRepository,
    private val permissions: PermissionRepository,
    private val featureUsage: FeatureUsageRepository,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(
    // Route arguments ARE the initial state, read once (MVI §3 rule 5); so is the fixed layout.
    HomeState(
        sections = HomeSections.build(),
        arrivedFromNotification = savedStateHandle.arrivedFromNotification(),
        pendingDeepLink = savedStateHandle.pendingFeature(),
    ),
    log,
) {

    init {
        // init OBSERVES, never acts. UNKNOWN — the one exception the appendix allows,
        // TrackFirstOpenUseCase, is a `:domain` use case and this cluster does not own that package.
        storageInfo.observe().collectSafely(onError = ::report) { info ->
            setState { copy(storage = StorageUsage(info.totalBytes, info.availableBytes)) }
        }
        cleanupLedger.observeLifetimeFreedBytes().collectSafely(onError = ::report) { bytes ->
            setState { copy(lifetimeSavedBytes = bytes) }
        }
        junk.cachedJunkBytes().collectSafely(onError = ::report) { bytes ->
            setState { copy(junkPill = bytes.toJunkPill()) }
        }
        // Re-emits on ProcessLifecycle RESUMED, which is what makes the funnel work: the user grants
        // in Settings, presses back, and this same reducer is re-entered (LLM.md §7.4).
        permissions.observe().collectSafely(onError = ::report) { granted ->
            setState {
                copy(
                    showPermissionWarning =
                        HomeSections.permissionsBehindTiles.any { it !in granted },
                    showNotificationBanner = AppPermission.Notifications !in granted,
                )
            }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ScreenStarted -> onScreenStarted()
            HomeIntent.ScreenResumed -> onScreenResumed()
            // UNKNOWN — the 1 Hz download-rate ticker is `NetworkTrafficRepository`
            // (`networkDataModule`), likewise absent, so there is no job to stop.
            HomeIntent.ScreenStopped -> Unit
            is HomeIntent.FeatureTapped -> openFeature(intent.feature)
            HomeIntent.CleanTapped -> openFeature(HomeSections.heroFeature)
            HomeIntent.SettingsTapped -> sendEffect(HomeEffect.NavigateToSettings)
            HomeIntent.PermissionWarningTapped -> sendEffect(HomeEffect.NavigateToPermissionCentre)
            HomeIntent.NotificationBannerTapped -> sendEffect(HomeEffect.RequestNotificationPermission)
            HomeIntent.BackPressed -> onBackPressed()
            is HomeIntent.NotificationSheetAnswered -> {
                setState { copy(dialog = null, hasOfferedNotificationSheet = true) }
                if (intent.accepted) sendEffect(HomeEffect.RequestNotificationPermission)
            }
            is HomeIntent.SecurityConsentAnswered -> onSecurityConsentAnswered(intent.accepted)
            is HomeIntent.ExitOfferAnswered -> onExitOfferAnswered(intent.accepted)
            HomeIntent.ExitOfferDismissed -> setState { copy(dialog = null) }
            is HomeIntent.LinkTapped -> sendEffect(HomeEffect.OpenLegalDocument(intent.document))
            is HomeIntent.NotificationPermissionResolved ->
                setState { copy(showNotificationBanner = !intent.granted) }
            is HomeIntent.PermissionResolved -> onPermissionResolved(intent.granted)
        }
    }

    /**
     * The deep link fires **after** the first composition, once, and is cleared so a second
     * `ScreenStarted` cannot re-fire it. The competitor runs it as step 4 of 14 before its views
     * exist, so a feature Activity can start on top of a half-built home (delta 9).
     */
    private fun onScreenStarted() {
        val pending = currentState.pendingDeepLink ?: return
        setState { copy(pendingDeepLink = null) }
        openFeature(pending)
    }

    /**
     * ONE refresh pass, against the competitor's eight uncoordinated ones (delta 16): the rest of the
     * page is push-driven by the four flows in `init`. The busy flag is raised before the call and
     * lowered in **every** arm, `onError` and the timeout included — lowered only in the happy arm it
     * is a spinner that outlives its work. TTL and single-flight stay in the repository.
     */
    private fun onScreenResumed() {
        setState { copy(error = null) }
        if (currentState.shouldOfferNotificationSheet()) {
            setState { copy(dialog = HomeDialog.NotificationPermission, hasOfferedNotificationSheet = true) }
        }
        if (currentState.isJunkEstimating) return
        setState { copy(isJunkEstimating = true) }
        launchSafely(onError = { setState { copy(isJunkEstimating = false, error = it) } }) {
            val result = withTimeoutOrNull(JUNK_ESTIMATE_TIMEOUT) { junk.refreshEstimate() }
            setState { copy(isJunkEstimating = false) }
            // A timeout surfaces nothing: the cached figure is still honest, and the flag is down.
            if (result is AppResult.Failure) report(result.error)
        }
    }

    /**
     * ONE reducer for all twenty entry points, replacing twenty methods and a 14-lambda router gate.
     * **The analytics id and the destination are the same enum constant** — kept apart as two
     * literals in two code paths, they produced the live `100525`/`100526` transposition (delta 10).
     * Split from [resolveFeature], which a permission or consent answer re-enters: counting the tap
     * again there would inflate every gated feature by the number of prompts the user saw.
     *
     * The `FeatureAvailability` gate is the first line for the same reason: all four routes into a
     * feature pass through here, and only one of them — a tile tap — goes through a composable that
     * can disable itself. Nothing is tracked or marked used, because a feature that could not be
     * opened was not opened, and counting it would put a figure in the ledger no screen rendered.
     */
    private fun openFeature(feature: FeatureId) {
        if (!FeatureAvailability.isAvailable(feature)) return
        analytics.track(AnalyticsEvent.FeatureOpened(feature))
        // Bookkeeping the user did not ask for: fire and forget, a failure is logged, never surfaced.
        launchSafely { markFeatureUsed(feature) }
        resolveFeature(feature)
    }

    /** One `sendEffect`, so "never both, never zero" is structural rather than asserted. */
    private fun resolveFeature(feature: FeatureId) {
        if (feature == FeatureId.Antivirus && !currentState.hasAcceptedSecurityConsent) {
            setState { copy(dialog = HomeDialog.AntivirusConsent) }
            return
        }
        val missing = permissions.missingFor(feature).firstOrNull()
        setState { copy(permissionGate = if (missing == null) null else feature) }
        sendEffect(
            if (missing == null) HomeEffect.NavigateToFeature(feature)
            else HomeEffect.RequestPermission(missing),
        )
    }

    private fun onPermissionResolved(granted: Boolean) {
        val gated = currentState.permissionGate ?: return
        setState { copy(permissionGate = null) }
        if (granted) resolveFeature(gated)
    }

    /** The re-entrancy guard is in the **reducer**, not the UI: two BACK presses raise one dialog. */
    private fun onBackPressed() {
        if (currentState.dialog != null) return
        launchSafely(onError = { sendEffect(HomeEffect.ExitApp) }) {
            val offer = featureUsage.recommend()
            setState { copy(dialog = HomeDialog.ExitOffer(offer)) }
            analytics.track(AnalyticsEvent.ExitOfferShown(offer))
        }
    }

    private fun onExitOfferAnswered(accepted: Boolean) {
        val offer = currentState.dialog as? HomeDialog.ExitOffer
        setState { copy(dialog = null) }
        // Accepting opens a feature the user had not opened, so it IS a feature open, and is counted.
        val opened = offer?.openedFeature(accepted)
        if (opened != null) openFeature(opened) else sendEffect(HomeEffect.ExitApp)
    }

    /**
     * PENDING OWNER DECISION — the appendix asserts the consent is **persisted before**
     * `NavigateToFeature`, and nothing persists it: `AppSettingsRepository` has no declared owner
     * (`docs/screens/11-home.md` §4.3 item 1). The order is kept so the write is one suspending line
     * to add; the flag is session-scoped until then.
     */
    private fun onSecurityConsentAnswered(accepted: Boolean) {
        setState { copy(dialog = null, hasAcceptedSecurityConsent = accepted) }
        // `resolveFeature`, not `openFeature`: this tap was counted when the dialog was raised.
        if (accepted) resolveFeature(FeatureId.Antivirus)
    }

    /** The effect CARRIES the error: the collector runs a frame before the matching state renders. */
    private fun report(error: AppError) {
        setState { copy(error = error) }
        sendEffect(HomeEffect.ShowMessage(error))
    }
}
