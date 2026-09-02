package com.pion.phonecleaner.feature.onboarding.splash

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.launch.LaunchSource
import com.pion.phonecleaner.domain.model.onboarding.OnboardingState
import com.pion.phonecleaner.domain.model.onboarding.SplashPacing
import com.pion.phonecleaner.feature.onboarding.testing.FakeConsentHost
import com.pion.phonecleaner.feature.onboarding.testing.FakeConsentRepository
import com.pion.phonecleaner.feature.onboarding.testing.FakeOnboardingStateRepository
import com.pion.phonecleaner.feature.onboarding.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.onboarding.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.onboarding.testing.runVmTest
import com.pion.phonecleaner.feature.onboarding.testing.settle
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** `docs/screens/10-splash-and-onboarding.md` §5.2 — the categories this ViewModel owes. */
internal class SplashViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val pacing = SplashPacing(timeoutMillis = 5_000L, progressSteps = 100)

    private fun viewModel(
        saved: OnboardingState = OnboardingState(),
        consent: FakeConsentRepository = FakeConsentRepository(),
        onboarding: FakeOnboardingStateRepository = FakeOnboardingStateRepository(saved),
    ) = SplashViewModel(
        savedStateHandle = SavedStateHandle(mapOf("source" to LaunchSource.Notification.name)),
        onboardingState = onboarding,
        consent = consent,
        permissions = FakePermissionRepository(),
        pacing = pacing,
        log = AppLogger.NoOp,
    )

    // ── Reducer ────────────────────────────────────────────────────────────────────────────────
    @Test
    fun `the route argument is the initial state`() = main.runVmTest {
        assertEquals(LaunchSource.Notification, viewModel().state.value.source)
    }

    @Test
    fun `ticking the box flips the flag and navigates nowhere by itself`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(SplashIntent.PoliciesAcceptanceChanged(true))
        assertTrue(vm.state.value.hasAcceptedPolicies)
        assertFalse(vm.state.value.isLeaving)
    }

    @Test
    fun `an unticked box is the default, against the competitor's pre-ticked one`() =
        main.runVmTest {
            assertFalse(viewModel().state.value.hasAcceptedPolicies)
        }

    // ── Effects ────────────────────────────────────────────────────────────────────────────────
    @Test
    fun `continue with the box unticked complains exactly once and does not navigate`() =
        main.runVmTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onIntent(SplashIntent.ContinueRequested)
                assertIs<SplashEffect.ShowPoliciesRequired>(awaitItem())
                expectNoEvents()
            }
        }

    @Test
    fun `the notification latch is written only after an answer`() = main.runVmTest {
        val onboarding = FakeOnboardingStateRepository()
        val vm = viewModel(onboarding = onboarding)
        vm.onIntent(SplashIntent.ScreenStarted)
        assertFalse("notificationAsk" in onboarding.writes)
        vm.onIntent(SplashIntent.NotificationPermissionResolved(granted = false))
        assertTrue("notificationAsk" in onboarding.writes)
    }

    // ── Stuck state ────────────────────────────────────────────────────────────────────────────
    @Test
    fun `a returning user leaves on the ramp's own deadline`() = main.runVmTest {
        val vm = viewModel(
            saved = OnboardingState(hasAcceptedTerms = true, hasAnsweredNotificationAsk = true),
        )
        vm.effects.test {
            vm.onIntent(SplashIntent.ScreenStarted)
            assertIs<SplashEffect.RequestConsent>(awaitItem())
            vm.onIntent(SplashIntent.ConsentHostReady(FakeConsentHost()))
            settle(pacing.timeoutMillis)
            assertIs<SplashEffect.NavigateToDeviceCheck>(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(SplashState.PROGRESS_MAX, vm.state.value.progress)
        assertTrue(vm.state.value.hasServedMinimumTime)
    }

    @Test
    fun `a first-run user is held by the consent gate, not by the clock`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(SplashIntent.ScreenStarted)
        vm.onIntent(SplashIntent.NotificationPermissionResolved(granted = true))
        vm.onIntent(SplashIntent.ConsentHostReady(FakeConsentHost()))
        settle(pacing.timeoutMillis)

        // The bar finished and the gate settled — and the user is still here, because nothing has
        // been agreed to. `canLeave` is the whole chain, so the box is what is missing.
        assertTrue(vm.state.value.isProgressComplete)
        assertTrue(vm.state.value.isConsentGateSettled)
        assertFalse(vm.state.value.canLeave)
        assertFalse(vm.state.value.isLeaving)
        assertTrue(vm.state.value.isConsentPanelVisible)
    }

    @Test
    fun `the terms latch is written when the user leaves with the box ticked`() = main.runVmTest {
        val onboarding = FakeOnboardingStateRepository()
        val vm = viewModel(onboarding = onboarding)
        vm.onIntent(SplashIntent.ScreenStarted)
        vm.onIntent(SplashIntent.NotificationPermissionResolved(granted = true))
        vm.onIntent(SplashIntent.ConsentHostReady(FakeConsentHost()))
        settle(pacing.timeoutMillis)
        vm.onIntent(SplashIntent.PoliciesAcceptanceChanged(true))
        vm.onIntent(SplashIntent.ContinueRequested)

        assertTrue(vm.state.value.isLeaving)
        assertTrue("terms" in onboarding.writes)
        // The first-run latch is HOME's write, not the splash's (`docs/screens/11-home.md` delta 17).
        assertFalse("firstRun" in onboarding.writes)
    }

    @Test
    fun `the consent gate settles on any answer, so the round trip cannot strand the user`() =
        main.runVmTest {
            val consent = FakeConsentRepository()
            val vm = viewModel(
                saved = OnboardingState(hasAcceptedTerms = true, hasAnsweredNotificationAsk = true),
                consent = consent,
            )
            vm.onIntent(SplashIntent.ScreenStarted)
            vm.onIntent(SplashIntent.ConsentHostReady(FakeConsentHost()))
            assertEquals(1, consent.requests)
            assertTrue(vm.state.value.isConsentGateSettled)
        }
}
