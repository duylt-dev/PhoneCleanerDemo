package com.pion.phonecleaner.feature.device.devicestatusscan

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.usecase.ReadDeviceMetricsUseCase
import com.pion.phonecleaner.feature.device.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceMetricsRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceScanSessionStore
import com.pion.phonecleaner.feature.device.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.device.testing.runVmTest
import com.pion.phonecleaner.feature.device.testing.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class DeviceStatusScanViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val metrics = FakeDeviceMetricsRepository()
    private val session = FakeDeviceScanSessionStore()
    private val analytics = FakeAnalyticsRepository()

    private fun viewModel() = DeviceStatusScanViewModel(
        session = session,
        readDeviceMetrics = ReadDeviceMetricsUseCase(metrics),
        analytics = analytics,
        log = AppLogger.NoOp,
    )

    /** The whole point of §2.5: the wait does the reading, and the result crosses the edge. */
    @Test
    fun `the scan writes its readings to the session store before navigating`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()

            vm.effects.test {
                settle()
                assertEquals(DeviceStatusScanEffect.NavigateToDetail, awaitItem())
                expectNoEvents()
            }

            assertNotNull(session.deviceMetrics)
            assertEquals("Pion P1", session.deviceMetrics?.identity?.manufacturerAndModel)
            assertEquals(100, vm.state.value.progress)
            assertTrue(vm.state.value.isFinished)
            assertFalse(vm.state.value.isBackBlocked)
        }

    /** Back is refused while the beat runs, and it is a message — not a silently ignored press. */
    @Test
    fun `back during the scan asks for the message and does not navigate`() =
        mainDispatcher.runVmTest {
            metrics.readDelayMillis = 10_000L
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(DeviceStatusScanIntent.BackPressed)
                assertEquals(DeviceStatusScanEffect.ShowScanInProgressMessage, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(vm.state.value.isBackBlocked)
        }

    @Test
    fun `back after the scan navigates back`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        // The channel buffers, so the scan's own navigation is still queued: it is consumed first,
        // exactly as the Route consumes it, before the Back press is asserted on.
        vm.effects.test {
            settle()
            assertEquals(DeviceStatusScanEffect.NavigateToDetail, awaitItem())

            vm.onIntent(DeviceStatusScanIntent.BackPressed)
            assertEquals(DeviceStatusScanEffect.NavigateBack, awaitItem())
        }
    }

    /**
     * Crash containment **and** the stuck state in one: a thrown read reaches `launchSafely`'s third
     * path, which neither `AppResult` arm covers, and the flag it must lower is the one that refuses
     * Back. A screen that cannot be left is worse than one that reports a failure.
     */
    @Test
    fun `a thrown read lowers the back block as well as setting the error`() =
        mainDispatcher.runVmTest {
            metrics.throwOnMemory = true
            val vm = viewModel()
            settle()

            assertTrue(vm.state.value.error is AppError.Unexpected)
            assertFalse(vm.state.value.isBackBlocked)
            assertFalse(vm.state.value.isFinished)
            assertNull(session.deviceMetrics)
        }

    /** `FeatureOpened` fires here and on no other device-status screen, so the funnel counts once. */
    @Test
    fun `the feature open is tracked exactly once`() = mainDispatcher.runVmTest {
        viewModel()
        settle()

        assertEquals(
            listOf(AnalyticsEvent.FeatureOpened(FeatureId.DeviceStatus)),
            analytics.events,
        )
    }
}
