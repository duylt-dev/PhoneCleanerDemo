package com.pion.phonecleaner.feature.device.batteryinfo

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ObserveBatteryUseCase
import com.pion.phonecleaner.feature.device.testing.FakeBatteryRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceScanSessionStore
import com.pion.phonecleaner.feature.device.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.device.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.device.testing.batterySnapshot
import com.pion.phonecleaner.feature.device.testing.runVmTest
import com.pion.phonecleaner.feature.device.testing.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class BatteryInfoViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val battery = FakeBatteryRepository()
    private val session = FakeDeviceScanSessionStore()
    private val featureUsage = FakeFeatureUsageRepository()

    private fun viewModel() = BatteryInfoViewModel(
        session = session,
        observeBattery = ObserveBatteryUseCase(battery),
        markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
        log = AppLogger.NoOp,
    )

    /** A session is consumed once: the seed is taken, and the slot is emptied behind it (§5.2). */
    @Test
    fun `the seed from the scan fills the first frame and clears the store`() =
        mainDispatcher.runVmTest {
            session.batterySnapshot = batterySnapshot(percent = 22)
            battery.snapshots = emptyList()

            val vm = viewModel()

            assertEquals(22, vm.state.value.snapshot?.percent)
            assertNull(session.batterySnapshot)
        }

    /**
     * The session-lost branch §0.2 makes mandatory: an empty store — process death, or a deep link —
     * starts `isLoading` and the live feed fills it in, rather than rendering a blank frame.
     */
    @Test
    fun `an empty store starts loading and the feed fills it`() = mainDispatcher.runVmTest {
        battery.snapshots = listOf(batterySnapshot(percent = 77))

        val vm = viewModel()
        settle()

        assertEquals(77, vm.state.value.snapshot?.percent)
        assertEquals(false, vm.state.value.isLoading)
        assertEquals(1, battery.subscriptions)
    }

    @Test
    fun `opening the screen records the feature as used`() = mainDispatcher.runVmTest {
        viewModel()
        settle()

        assertEquals(listOf(FeatureId.BatteryInfo), featureUsage.marked)
    }

    /** Crash containment: a thrown feed becomes an error banner, never a thrown test. */
    @Test
    fun `a thrown feed reaches the error field`() = mainDispatcher.runVmTest {
        battery.throwOnObserve = true

        val vm = viewModel()
        settle()

        assertTrue(vm.state.value.error is AppError.Unexpected)
        assertNull(vm.state.value.snapshot)
    }

    @Test
    fun `back navigates back`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(BatteryInfoIntent.BackPressed)
            assertEquals(BatteryInfoEffect.NavigateBack, awaitItem())
            expectNoEvents()
        }
    }
}
