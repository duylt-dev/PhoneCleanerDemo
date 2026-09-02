package com.pion.phonecleaner.feature.onboarding.splash

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.onboarding.ConsentHost
import com.pion.phonecleaner.domain.model.onboarding.SplashPacing
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.domain.repository.ConsentRepository
import com.pion.phonecleaner.domain.repository.OnboardingStateRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Replaces `CucurtagActivity` (691 L) + `Skiphitec` (106 L)
 * (`docs/screens/10-splash-and-onboarding.md` §1.2).
 *
 * `init` **observes and does not act** (MVI §3.3). The ramp is not started there either: it starts
 * from [SplashIntent.NotificationPermissionResolved], exactly as the competitor defers it to `d0()`.
 *
 * No `android.*` and no `androidx.compose.*` import appears in this file. `SavedStateHandle` is
 * `androidx.lifecycle`, the same package the base `ViewModel` comes from.
 *
 * ANALYTICS — deliberately absent. §1.2 injects `AnalyticsRepository`, but `AnalyticsEvent`
 * (`:domain/repository/AnalyticsRepository.kt`) is a sealed interface with three arms — `FeatureOpened`,
 * `ExitOfferShown`, `CleanRequested` — and none of them describes a splash. A sealed interface admits
 * implementations only in its own module, that file belongs to another owner, and no verified wire id
 * exists for a splash event anywhere in the corpus. Injecting an unused `AnalyticsRepository` would
 * look wired without being wired, so nothing is injected and nothing is fabricated.
 */
class SplashViewModel(
    savedStateHandle: SavedStateHandle,
    private val onboardingState: OnboardingStateRepository,
    private val consent: ConsentRepository,
    private val permissions: PermissionRepository,
    private val pacing: SplashPacing,
    log: AppLogger,
) : MviViewModel<SplashState, SplashIntent, SplashEffect>(
    // Route arguments ARE the initial state (MVI §3 rule 5).
    SplashState(source = savedStateHandle.launchSource()),
    log,
) {

    private var rampJob: Job? = null

    /** `ON_START` arrives again after a policy page closes; the sequence must start exactly once. */
    private var hasStarted = false

    init {
        // One record, one write point per flag, observed as a Flow — the competitor's three
        // disagreeing first-run flags are delta 10.
        onboardingState.observe().collectSafely(onError = ::report) { saved ->
            setState {
                copy(
                    isConsentPanelVisible = !saved.hasAcceptedTerms,
                    hasAcceptedPolicies = hasAcceptedPolicies || saved.hasAcceptedTerms,
                    isNotificationAskSettled =
                        isNotificationAskSettled || saved.hasAnsweredNotificationAsk,
                    hasSeenDeviceCheck = saved.hasSeenDeviceCheck,
                )
            }
            // The latches arrive on a Flow, not from an intent, so for a returning user they can be
            // the LAST guard to fall. Without this the ramp's own `tryLeave()` is the only attempt
            // and a slow store read strands the user on a finished splash. `canLeave` is the whole
            // chain, so re-running it here cannot leave early.
            tryLeave()
        }
    }

    override fun onIntent(intent: SplashIntent) {
        when (intent) {
            SplashIntent.ScreenStarted -> begin()
            is SplashIntent.NotificationPermissionResolved -> onNotificationAnswered()
            is SplashIntent.ConsentHostReady -> requestConsent(intent.host)
            is SplashIntent.PoliciesAcceptanceChanged ->
                // Flips the flag and nothing else: leaving is ContinueRequested's decision, so a tick
                // never navigates by itself while the panel is on screen.
                setState { copy(hasAcceptedPolicies = intent.accepted) }

            SplashIntent.PrivacyPolicyClicked -> openPolicy(LegalDocument.PrivacyPolicy)
            SplashIntent.TermsClicked -> openPolicy(LegalDocument.TermsOfService)
            SplashIntent.ReturnedFromPolicyPage -> {
                setState { copy(isPolicyPageOpen = false) }
                tryLeave()
            }

            SplashIntent.ContinueRequested ->
                if (!currentState.hasAcceptedPolicies) sendEffect(SplashEffect.ShowPoliciesRequired)
                else tryLeave()
        }
    }

    private fun begin() {
        if (hasStarted) return
        hasStarted = true
        // Asking for a permission the app already holds burns the one dialog Android grants.
        val alreadyHeld = permissions.isGranted(AppPermission.Notifications)
        if (currentState.isNotificationAskSettled || alreadyHeld) onNotificationAnswered()
        else sendEffect(SplashEffect.RequestNotificationPermission)
    }

    /**
     * The latch is written **after** an answer. `CucurtagActivity:678` writes its flag `false` before
     * showing the dialog, so a swipe-away or a process death burns the only ask (delta 2).
     */
    private fun onNotificationAnswered() {
        setState { copy(isNotificationAskSettled = true) }
        launchSafely(onError = ::report) { onboardingState.markNotificationAskAnswered() }
        sendEffect(SplashEffect.RequestConsent)
        startRamp()
    }

    private fun requestConsent(host: ConsentHost) {
        launchSafely(onError = { settleConsentGate() }) {
            // Delta 3: the competitor throws the return code away (`f0`, `CucurtagActivity:274`).
            // Here it is reduced, and `canRequestAds` reaches the ad boundary through the repository.
            when (val answer = consent.requestConsent(host)) {
                is AppResult.Success -> log.d { "Consent settled: ${answer.value.outcome}" }
                is AppResult.Failure -> log.d { "Consent unavailable: ${answer.error}" }
            }
            settleConsentGate()
        }
    }

    /** The gate settles on ANY answer, including a failure: a user is never held by an ad decision. */
    private fun settleConsentGate() {
        setState { copy(isConsentGateSettled = true) }
        tryLeave()
    }

    private fun openPolicy(page: LegalDocument) {
        setState { copy(isPolicyPageOpen = true) }
        sendEffect(SplashEffect.OpenPolicyPage(page))
    }

    /**
     * `Skiphitec.d()` overwrites `loadingJob` without cancelling it, and `d()` runs twice on the
     * restart path (`:91-93`, delta 9). Here the old job is cancelled and replaced, and the ticker is
     * a **structural child** so an unfinished child cannot keep the parent from completing.
     */
    private fun startRamp() {
        rampJob?.cancel()
        rampJob = launchSafely(
            onError = { setState { copy(error = it, progress = SplashState.PROGRESS_MAX, hasServedMinimumTime = true) } },
        ) {
            val step = (pacing.timeoutMillis / pacing.progressSteps).coerceAtLeast(1L)
            val ticker = launch {
                repeat(pacing.progressSteps) { i ->
                    delay(step)
                    setState { copy(progress = ((i + 1) * SplashState.PROGRESS_MAX) / pacing.progressSteps) }
                }
            }
            try {
                // Bound every wait (MVI §3): `withTimeoutOrNull`, so an overrun lands in the same
                // path as a normal finish instead of in a catch.
                //
                // [AD GATE: app-open] — `ads.awaitInventory(AppOpen, source.placement)` is the wait
                // this deadline was sized for. The ad boundary is carried as "boundaries only,
                // internals out of scope" (`docs/system-architecture.md` §5.9) and has no module
                // owner (§5.3 open item 2), so nothing is called and nothing is faked. Adding it
                // means racing it against this same deadline here; nothing above changes.
                withTimeoutOrNull(pacing.timeoutMillis) { ticker.join() }
                setState { copy(progress = SplashState.PROGRESS_MAX, hasServedMinimumTime = true) }
                tryLeave()
            } finally {
                ticker.cancel()
            }
        }
    }

    /** `r0()`'s guard chain, once, and nowhere else. */
    private fun tryLeave() {
        if (!currentState.canLeave) return
        setState { copy(isLeaving = true) }
        // Only the terms latch. `markFirstRunCompleted()` is HOME's write, on home's first creation,
        // matching `EstissueActivity.z1()` (`docs/screens/11-home.md:356` delta 17); writing it here
        // would flip the first-launch branch one screen early, and two writers to one latch is the
        // shape `LLM.md` §6.4 exists to prevent.
        launchSafely(onError = ::report) { onboardingState.markTermsAccepted() }
        sendEffect(
            if (currentState.shouldShowDeviceCheck) SplashEffect.NavigateToDeviceCheck
            else SplashEffect.NavigateToHome,
        )
    }

    /**
     * A failed latch write or a failed preference read must not hold the user on a splash, so the
     * error is recorded and the flow continues. `viewModelScope` is cancelled with the ViewModel, so
     * `rampJob` needs no `onCleared` override to be cancelled.
     */
    private fun report(error: AppError) = setState { copy(error = error) }
}
