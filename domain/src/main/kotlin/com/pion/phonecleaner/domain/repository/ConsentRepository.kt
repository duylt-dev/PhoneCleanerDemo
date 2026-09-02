package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.onboarding.ConsentHost
import com.pion.phonecleaner.domain.model.onboarding.ConsentStatus
import kotlinx.coroutines.flow.Flow

/**
 * The privacy-consent gateway — **a repository, not a screen**
 * (`docs/screens/10-splash-and-onboarding.md` §4). No Contract, no ViewModel, no route: the form is
 * Google's own Activity-hosted dialog, so all this side owns is a suspend call and the decision it
 * yields.
 *
 * Replaces `md.l3` (185 L), a Kotlin `object` with one static `ConsentInformation` field that throws
 * `throwUninitializedPropertyAccessException` on eight paths (`java/md/l3.java:23`), and whose `k()`
 * calls an empty `j()` on every branch (`:163-172`, `:183-184`).
 *
 * **OWNERSHIP IS UNRESOLVED** — the same gap as `OnboardingStateRepository`
 * (`docs/screens/10-splash-and-onboarding.md` §5.3 open item 1). It ships with the onboarding
 * cluster's own `:data` module and is reported to the assembly point, not wired into `:app` here.
 */
interface ConsentRepository {

    /**
     * Runs the round trip and returns what it decided. Suspends for as long as the form is on screen.
     *
     * The Route calls this — the ViewModel raises `SplashEffect.RequestConsent` and reduces
     * `SplashIntent.ConsentResolved`, so nothing in `SplashViewModel` imports `android.app.Activity`.
     *
     * A `Failure` is a real outcome, not a crash: the splash settles its consent gate on it and
     * continues. `AppResult` is the contract; exceptions never cross this boundary (MVI §5).
     */
    suspend fun requestConsent(host: ConsentHost): AppResult<ConsentStatus>

    /**
     * The current decision, re-emitting when it changes. Read by whoever owns the ad boundary; the
     * splash does not need it, because it acts on the answer to its own request.
     */
    fun observeStatus(): Flow<ConsentStatus>
}
