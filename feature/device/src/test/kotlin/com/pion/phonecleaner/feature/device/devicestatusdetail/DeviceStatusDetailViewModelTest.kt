package com.pion.phonecleaner.feature.device.devicestatusdetail

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.device.DeviceIdentity
import com.pion.phonecleaner.domain.model.device.DeviceMetrics
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ObserveBatteryUseCase
import com.pion.phonecleaner.domain.usecase.ReadDeviceMetricsUseCase
import com.pion.phonecleaner.feature.device.testing.FakeBatteryRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceMetricsRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceScanSessionStore
import com.pion.phonecleaner.feature.device.testing.FakeFeatureUsageRepository
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

internal class DeviceStatusDetailViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val metrics = FakeDeviceMetricsRepository()
    private val battery = FakeBatteryRepository()
    private val session = FakeDeviceScanSessionStore()
    private val featureUsage = FakeFeatureUsageRepository()

    private fun viewModel() = DeviceStatusDetailViewModel(
        session = session,
        readDeviceMetrics = ReadDeviceMetricsUseCase(metrics),
        observeBattery = ObserveBatteryUseCase(battery),
        markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
        log = AppLogger.NoOp,
    )

    /** The scan's readings seed the first frame, and the slot is emptied behind them (§0.2). */
    @Test
    fun `the session seed fills the cards and is consumed`() = mainDispatcher.runVmTest {
        session.deviceMetrics = DeviceMetrics(identity = DeviceIdentity("Seeded", "13"))
        metrics.readDelayMillis = 10_000L

        val vm = viewModel()

        assertEquals("Seeded", vm.state.value.identity?.manufacturerAndModel)
        assertNull(session.deviceMetrics)
    }

    /** Six nullables: the page is usable the moment any one lands, and the battery ticks live. */
    @Test
    fun `every card lands and the battery arrives from the shared feed`() =
        mainDispatcher.runVmTest {
            battery.snapshots = listOf(batterySnapshot(percent = 33))

            val vm = viewModel()
            settle()

            val state = vm.state.value
            assertTrue(state.hasAnyCard)
            assertEquals(33, state.battery?.percent)
            assertEquals(8, state.cpu?.cores)
            assertFalse(state.isLoading)
            assertNull(state.error)
        }

    /**
     * The stuck state: a thrown read reaches `launchSafely`'s third path, which no `AppResult` arm
     * covers, and the spinner must still come down.
     */
    @Test
    fun `a thrown read lowers the spinner as well as setting the error`() =
        mainDispatcher.runVmTest {
            metrics.throwOnMemory = true

            val vm = viewModel()
            settle()

            assertFalse(vm.state.value.isLoading)
            assertTrue(vm.state.value.error is AppError.Unexpected)
        }

    /** And the next attempt still reaches the repository — a failure is not a latch. */
    @Test
    fun `a retry after a failure reaches the repository again`() = mainDispatcher.runVmTest {
        metrics.throwOnMemory = true
        val vm = viewModel()
        settle()
        val callsAfterFailure = metrics.memoryCalls

        metrics.throwOnMemory = false
        vm.onIntent(DeviceStatusDetailIntent.RetryTapped)
        settle()

        assertTrue(metrics.memoryCalls > callsAfterFailure)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    /** A resume re-reads. The competitor's page is a snapshot taken once, in `onCreate` (§3.5). */
    @Test
    fun `a resume re-reads the metrics`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()
        val callsAfterFirstLoad = metrics.memoryCalls

        vm.onIntent(DeviceStatusDetailIntent.ScreenResumed)
        settle()

        assertTrue(metrics.memoryCalls > callsAfterFirstLoad)
    }

    /** Three Check buttons, three navigation effects, and this route stays on the back stack. */
    @Test
    fun `each check button raises its own navigation effect`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()

        vm.effects.test {
            vm.onIntent(DeviceStatusDetailIntent.CheckMemoryTapped)
            assertEquals(DeviceStatusDetailEffect.NavigateToRunningApps, awaitItem())

            vm.onIntent(DeviceStatusDetailIntent.CheckStorageTapped)
            assertEquals(DeviceStatusDetailEffect.NavigateToJunkClean, awaitItem())

            vm.onIntent(DeviceStatusDetailIntent.CheckBatteryTapped)
            assertEquals(DeviceStatusDetailEffect.NavigateToBattery, awaitItem())

            vm.onIntent(DeviceStatusDetailIntent.BackPressed)
            assertEquals(DeviceStatusDetailEffect.NavigateBack, awaitItem())
            expectNoEvents()
        }
    }

    /** Usage is recorded where the feature is *used*, not on the scan that leads to it (§2.2). */
    @Test
    fun `the detail screen is what records device-status usage`() = mainDispatcher.runVmTest {
        viewModel()
        settle()

        assertEquals(listOf(FeatureId.DeviceStatus), featureUsage.marked)
    }
}
