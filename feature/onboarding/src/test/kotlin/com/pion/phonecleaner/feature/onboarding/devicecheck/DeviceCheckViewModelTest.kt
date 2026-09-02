package com.pion.phonecleaner.feature.onboarding.devicecheck

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.onboarding.DeviceCheckPacing
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoField
import com.pion.phonecleaner.domain.model.onboarding.StepStatus
import com.pion.phonecleaner.feature.onboarding.testing.FakeDeviceCheckProbe
import com.pion.phonecleaner.feature.onboarding.testing.FakeOnboardingStateRepository
import com.pion.phonecleaner.feature.onboarding.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.onboarding.testing.runVmTest
import com.pion.phonecleaner.feature.onboarding.testing.settle
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class DeviceCheckViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val pacing = DeviceCheckPacing(
        leadInMillis = 300L,
        perRowMillis = 400L,
        countdownSeconds = 3,
    )

    /** `300 + 5 x 400 + 3 x 1 000`, against the competitor's 9 300 ms (delta 4). */
    private val wholeSequenceMillis = 5_300L

    private fun viewModel(
        probe: FakeDeviceCheckProbe = FakeDeviceCheckProbe(),
        onboarding: FakeOnboardingStateRepository = FakeOnboardingStateRepository(),
    ) = DeviceCheckViewModel(
        probe = probe,
        onboardingState = onboarding,
        pacing = pacing,
        log = AppLogger.NoOp,
    )

    @Test
    fun `the empty list is the initial state`() = main.runVmTest {
        assertTrue(viewModel().state.value.rows.isEmpty())
        assertNull(viewModel().state.value.currentRowIndex)
    }

    @Test
    fun `the seen latch is written when the screen opens`() = main.runVmTest {
        val onboarding = FakeOnboardingStateRepository()
        viewModel(onboarding = onboarding)
        assertTrue("deviceCheck" in onboarding.writes)
    }

    // ── Unbounded growth ───────────────────────────────────────────────────────────────────────
    @Test
    fun `the sequence reveals five rows and only five`() = main.runVmTest {
        val probe = FakeDeviceCheckProbe()
        val vm = viewModel(probe = probe)
        settle(wholeSequenceMillis)

        assertEquals(DeviceInfoField.entries.size, vm.state.value.rows.size)
        assertEquals(5, vm.state.value.rows.size)
        assertEquals(DeviceInfoField.entries.toList(), probe.reads.toList())
        assertTrue(vm.state.value.rows.all { it.status == StepStatus.Done })
        assertTrue(vm.state.value.rows.all { it.value != null })
    }

    @Test
    fun `the CTA comes up before the countdown, and the countdown navigates`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            // Five ScrollToRow effects, then the navigation. `collect`, never `collectLatest` —
            // dropping one of these is exactly what a cancelled collector would do.
            settle(wholeSequenceMillis)
            repeat(DeviceInfoField.entries.size) { assertIs<DeviceCheckEffect.ScrollToRow>(awaitItem()) }
            assertIs<DeviceCheckEffect.NavigateToHome>(awaitItem())
            expectNoEvents()
        }
        assertTrue(vm.state.value.isContinueEnabled)
        assertTrue(vm.state.value.isLeaving)
        assertEquals(0, vm.state.value.countdownSeconds)
    }

    @Test
    fun `continue leaves early, exactly once`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            settle(pacing.leadInMillis + pacing.perRowMillis * 2)
            cancelAndIgnoreRemainingEvents()
        }
        vm.effects.test {
            vm.onIntent(DeviceCheckIntent.ContinueClicked)
            assertIs<DeviceCheckEffect.NavigateToHome>(awaitItem())
            settle(wholeSequenceMillis)
            expectNoEvents()
        }
        assertTrue(vm.state.value.isLeaving)
    }

    @Test
    fun `back does nothing while the sequence runs and leaves once the CTA is live`() =
        main.runVmTest {
            val vm = viewModel()
            vm.onIntent(DeviceCheckIntent.BackPressed)
            assertTrue(!vm.state.value.isLeaving)

            settle(wholeSequenceMillis)
            assertTrue(vm.state.value.isContinueEnabled)
        }

    // ── Crash containment ──────────────────────────────────────────────────────────────────────
    @Test
    fun `a probe that throws leaves a screen the user can leave`() = main.runVmTest {
        val vm = viewModel(
            probe = FakeDeviceCheckProbe(throwOn = setOf(DeviceInfoField.ScreenResolution)),
        )
        settle(wholeSequenceMillis)

        assertIs<AppError.Unexpected>(vm.state.value.error)
        assertTrue(vm.state.value.isContinueEnabled)
    }

    @Test
    fun `a failed reading is reported and the sequence carries on`() = main.runVmTest {
        val vm = viewModel(probe = FakeDeviceCheckProbe(failOn = setOf(DeviceInfoField.StorageUsed)))
        settle(wholeSequenceMillis)

        assertIs<AppError.Storage>(vm.state.value.error)
        assertEquals(5, vm.state.value.rows.size)
        // The failed row keeps a null value: it renders as "not read", never as a number the user
        // cannot tell from a reading (delta 10).
        assertNull(vm.state.value.rows.last { it.field == DeviceInfoField.StorageUsed }.value)
        assertNotNull(vm.state.value.rows.first().value)
        assertTrue(vm.state.value.isContinueEnabled)
    }

    // ── Stuck state ────────────────────────────────────────────────────────────────────────────
    @Test
    fun `the clock stops while the screen is not resumed`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(DeviceCheckIntent.ScreenPaused)
        settle(wholeSequenceMillis * 4)
        assertTrue(vm.state.value.rows.isEmpty())

        vm.onIntent(DeviceCheckIntent.ScreenResumed)
        settle(wholeSequenceMillis)
        assertEquals(5, vm.state.value.rows.size)
        assertTrue(vm.state.value.isLeaving)
    }
}
