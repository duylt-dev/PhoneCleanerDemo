package com.pion.phonecleaner.feature.device.batteryscan

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.device.BatteryCheck
import com.pion.phonecleaner.domain.model.device.StepState
import com.pion.phonecleaner.domain.usecase.ObserveBatteryUseCase
import com.pion.phonecleaner.feature.device.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.device.testing.FakeBatteryRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceScanSessionStore
import com.pion.phonecleaner.feature.device.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.device.testing.batterySnapshot
import com.pion.phonecleaner.feature.device.testing.runVmTest
import com.pion.phonecleaner.feature.device.testing.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class BatteryScanViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val battery = FakeBatteryRepository()
    private val session = FakeDeviceScanSessionStore()
    private val analytics = FakeAnalyticsRepository()

    private fun viewModel() = BatteryScanViewModel(
        session = session,
        observeBattery = ObserveBatteryUseCase(battery),
        analytics = analytics,
        log = AppLogger.NoOp,
    )

    /** Every row from the first frame, in `BatteryCheck` order, none of them added later. */
    @Test
    fun `all rows exist before the timeline starts`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        assertEquals(BatteryCheck.entries.size, vm.state.value.rows.size)
        assertTrue(vm.state.value.rows.all { it.state == StepState.Idle })
        assertTrue(vm.state.value.isBackBlocked)
    }

    /** §4.5's headline delta: the steps now check the things they name. */
    @Test
    fun `the reading reaches the session store and every row finishes`() =
        mainDispatcher.runVmTest {
            battery.snapshots = listOf(batterySnapshot(percent = 41))
            val vm = viewModel()

            vm.effects.test {
                settle()
                assertEquals(BatteryScanEffect.NavigateToBatteryInfo, awaitItem())
                expectNoEvents()
            }

            assertEquals(41, session.batterySnapshot?.percent)
            assertTrue(vm.state.value.rows.all { it.state == StepState.Done })
            assertTrue(vm.state.value.isFinished)
            assertFalse(vm.state.value.isBackBlocked)
        }

    /** The list is rebuilt by `copy`; nothing before the running row is left behind as Idle. */
    @Test
    fun `marking a row running settles every earlier row as done`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        // 300 ms lead-in plus two 550 ms steps puts the third check under way.
        testScheduler.advanceTimeBy(1_500L)
        testScheduler.runCurrent()

        val states = vm.state.value.rows.map { it.state }
        assertEquals(StepState.Done, states[0])
        assertEquals(StepState.Done, states[1])
        assertEquals(StepState.Running, states[2])
        assertEquals(StepState.Idle, states[3])
    }

    /** Crash containment and the stuck state: the flag that refuses Back must come down. */
    @Test
    fun `a thrown battery read lowers the back block as well as setting the error`() =
        mainDispatcher.runVmTest {
            battery.throwOnObserve = true
            val vm = viewModel()
            settle()

            assertTrue(vm.state.value.error is AppError.Unexpected)
            assertFalse(vm.state.value.isBackBlocked)
            assertNull(session.batterySnapshot)
        }

    @Test
    fun `back during the scan asks for the message`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(BatteryScanIntent.BackPressed)
            assertEquals(BatteryScanEffect.ShowScanInProgressMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
