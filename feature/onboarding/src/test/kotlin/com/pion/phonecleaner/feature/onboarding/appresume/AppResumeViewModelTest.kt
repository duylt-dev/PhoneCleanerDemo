package com.pion.phonecleaner.feature.onboarding.appresume

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.onboarding.AppResumePacing
import com.pion.phonecleaner.feature.onboarding.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.onboarding.testing.runVmTest
import com.pion.phonecleaner.feature.onboarding.testing.settle
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class AppResumeViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val pacing = AppResumePacing(
        rampMillis = 5_000L,
        progressSteps = 100,
        dismissDelayMillis = 350L,
    )

    private fun viewModel() = AppResumeViewModel(pacing = pacing, log = AppLogger.NoOp)

    @Test
    fun `the ramp is not started in init`() = main.runVmTest {
        val vm = viewModel()
        settle(pacing.rampMillis + pacing.dismissDelayMillis)
        assertEquals(0, vm.state.value.progress)
    }

    @Test
    fun `it dismisses itself after the ramp and the hand-off delay`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(AppResumeIntent.Shown)
            settle(pacing.rampMillis + pacing.dismissDelayMillis)
            assertIs<AppResumeEffect.Dismiss>(awaitItem())
            expectNoEvents()
        }
        assertEquals(AppResumeState.PROGRESS_MAX, vm.state.value.progress)
        assertTrue(vm.state.value.isDismissing)
    }

    @Test
    fun `back ends it once, and the ramp behind it cannot dismiss a second time`() =
        main.runVmTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onIntent(AppResumeIntent.Shown)
                vm.onIntent(AppResumeIntent.DismissRequested)
                assertIs<AppResumeEffect.Dismiss>(awaitItem())
                // The hand-off is a structural child of the cancelled job, so nothing arrives later —
                // `ScoutioneActivity` posts its 350 ms on the base Activity's raw Handler, which
                // nothing cancels.
                settle(pacing.rampMillis + pacing.dismissDelayMillis)
                expectNoEvents()
            }
        }

    @Test
    fun `a second Shown replaces the ramp instead of running two`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(AppResumeIntent.Shown)
            settle(pacing.rampMillis / 2)
            vm.onIntent(AppResumeIntent.Shown)
            settle(pacing.rampMillis + pacing.dismissDelayMillis)
            assertIs<AppResumeEffect.Dismiss>(awaitItem())
            expectNoEvents()
        }
    }
}
