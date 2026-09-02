package com.pion.phonecleaner.feature.onboarding.splash

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.launch.LaunchSource
import com.pion.phonecleaner.domain.model.onboarding.ConsentHost
import com.pion.phonecleaner.domain.model.settings.LegalDocument

/**
 * One immutable object folds the competitor's single `LiveData<Int>`, its **eleven public Activity
 * fields**, and two properties read straight off the ViewBinding — `natxpe.isChecked` and
 * `withdeabl.progress` (`docs/screens/10-splash-and-onboarding.md` §1.1).
 *
 * Eleven mutable fields on an Activity that never reads `savedInstanceState` is delta 8; one
 * `StateFlow` in a ViewModel plus a route argument read from `SavedStateHandle` is what replaces it,
 * so a rotation or a process death resumes where it was instead of restarting the flow.
 */
data class SplashState(
    /** Route argument, read from `SavedStateHandle` into the initial state. Never copied in later. */
    val source: LaunchSource = LaunchSource.Launcher,

    /** 0..[PROGRESS_MAX]. Competitor: `withdeabl.progress`, driven by a ramp that emitted every value twice. */
    val progress: Int = 0,

    /** Competitor: the `covular` row's VISIBLE/INVISIBLE, decided in `q0()` from `flux_first_flux_value`. */
    val isConsentPanelVisible: Boolean = false,

    /**
     * Competitor: `natxpe.isChecked`, pre-ticked in XML AND re-ticked in `q0()`.
     * Defaults to `false` here — the opposite of the competitor's default, on purpose (delta 1).
     * A pre-ticked box is not consent.
     */
    val hasAcceptedPolicies: Boolean = false,

    /** Competitor: `userMessageEnd`. True once the consent round trip has answered, whatever it said. */
    val isConsentGateSettled: Boolean = false,

    /** Competitor: `hasCheckNotifyPerm`. */
    val isNotificationAskSettled: Boolean = false,

    /** Competitor: `overTime` — the splash has served its minimum time. */
    val hasServedMinimumTime: Boolean = false,

    /** Competitor: `jumpToOtherPage > 0` — a policy page is on top. */
    val isPolicyPageOpen: Boolean = false,

    /** Competitor: `hasToHome` — the one-shot latch against a double navigation. */
    val isLeaving: Boolean = false,

    /**
     * `OnboardingStateRepository.observe().hasSeenDeviceCheck`, mirrored so the exit fork is decided
     * by the same reducer that decides everything else. Stored, not derived, because it arrives on a
     * Flow; [shouldShowDeviceCheck] is the derived half, so the two cannot disagree.
     */
    val hasSeenDeviceCheck: Boolean = false,

    val error: AppError? = null,
) : UiState {

    val isProgressComplete: Boolean get() = progress >= PROGRESS_MAX

    /** The exit fork of `r0()`. `docs/screens/10-splash-and-onboarding.md` §1.2 names it, §1.1 does not store it. */
    val shouldShowDeviceCheck: Boolean get() = !hasSeenDeviceCheck

    /**
     * The whole of `r0()`'s six-guard chain (`CucurtagActivity.java:606-627`) as ONE derived value.
     * Computed, never stored: two stored fields that can disagree eventually will.
     */
    val canLeave: Boolean
        get() = !isPolicyPageOpen && hasServedMinimumTime && isConsentGateSettled &&
            isProgressComplete && hasAcceptedPolicies && !isLeaving

    val isBusy: Boolean get() = isLeaving

    companion object {
        const val PROGRESS_MAX = 100
    }
}

/**
 * Every case below is raised by [com.pion.phonecleaner.feature.onboarding.splash.SplashRoute]. Two
 * cases the appendix lists are deliberately absent, because nothing in this module could raise them:
 *
 * - `LaunchSourceResolved` — the route argument IS the initial state (MVI §3 rule 5), read once from
 *   `SavedStateHandle`. An intent that re-reports it would be a second source for one value.
 * - `RelaunchedFromNotification` — `MainActivity` is the **one** `Intent` reader in the app
 *   (`LLM.md` §7.3), and a second notification tap is a navigation to `Route.Splash(newSource)`,
 *   which builds a new back-stack entry and therefore a new `SavedStateHandle`. Delta 5 is fixed in
 *   `:app`, where the Intent is read, not by a second path through this contract. The exact
 *   navigation call is reported with this change.
 */
sealed interface SplashIntent : UiIntent {

    /** `ON_START`. Idempotent — it arrives again after a policy page is dismissed. */
    data object ScreenStarted : SplashIntent

    /** The answer, not the ask. The latch is written from here, never before the dialog (delta 2). */
    data class NotificationPermissionResolved(val granted: Boolean) : SplashIntent

    /**
     * The Route's answer to [SplashEffect.RequestConsent]: the platform handle the consent form would
     * present itself over. Platform state lives in the composable and reports upward (MVI §4); the
     * suspend round trip itself belongs to the ViewModel, over `ConsentRepository`.
     */
    data class ConsentHostReady(val host: ConsentHost) : SplashIntent

    data class PoliciesAcceptanceChanged(val accepted: Boolean) : SplashIntent

    data object PrivacyPolicyClicked : SplashIntent

    data object TermsClicked : SplashIntent

    /** `ON_RESUME`. Clears `isPolicyPageOpen`; a no-op when no policy page was open. */
    data object ReturnedFromPolicyPage : SplashIntent

    /** The **only** intent that may complain out loud. Competitor delta 7. */
    data object ContinueRequested : SplashIntent
}

sealed interface SplashEffect : UiEffect {

    data object RequestNotificationPermission : SplashEffect

    /** Needs an Activity, so it leaves the ViewModel as an Effect and returns as [SplashIntent.ConsentHostReady]. */
    data object RequestConsent : SplashEffect

    data class OpenPolicyPage(val page: LegalDocument) : SplashEffect

    /** Competitor: the Toast of `@string/hallitiv`. Raised only by `ContinueRequested` (delta 7). */
    data object ShowPoliciesRequired : SplashEffect

    data object NavigateToDeviceCheck : SplashEffect

    data object NavigateToHome : SplashEffect
}
