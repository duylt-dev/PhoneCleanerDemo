package com.pion.phonecleaner.feature.device.runningappsscan

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.usecase.ListStoppableAppsUseCase
import com.pion.phonecleaner.domain.usecase.MarkRunningAppsScannedUseCase
import com.pion.phonecleaner.domain.usecase.ReadMemoryUseCase
import com.pion.phonecleaner.feature.device.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceMetricsRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceScanSessionStore
import com.pion.phonecleaner.feature.device.testing.FakeRunningAppsRepository
import com.pion.phonecleaner.feature.device.testing.FakeScanBadgeRepository
import com.pion.phonecleaner.feature.device.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.device.testing.runVmTest
import com.pion.phonecleaner.feature.device.testing.settle
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class RunningAppsScanViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val metrics = FakeDeviceMetricsRepository()
    private val runningApps = FakeRunningAppsRepository()
    private val badges = FakeScanBadgeRepository()
    private val session = FakeDeviceScanSessionStore()
    private val analytics = FakeAnalyticsRepository()

    private fun viewModel() = RunningAppsScanViewModel(
        session = session,
        readMemory = ReadMemoryUseCase(metrics),
        listStoppableApps = ListStoppableAppsUseCase(runningApps),
        markRunningAppsScanned = MarkRunningAppsScannedUseCase(badges),
        analytics = analytics,
        log = AppLogger.NoOp,
    )

    /**
     * §6.4's headline delta: **one** enumeration. The list crosses the `NavHost` edge in the session
     * store, so the next screen does not run the identical call again.
     */
    @Test
    fun `the enumeration runs once and its result reaches the session store`() =
        mainDispatcher.runVmTest {
            runningApps.apps = persistentListOf(RunningApp("com.x"), RunningApp("com.y"))
            val vm = viewModel()

            vm.effects.test {
                settle()
                assertEquals(RunningAppsScanEffect.NavigateToRunningApps, awaitItem())
                expectNoEvents()
            }

            assertEquals(1, runningApps.listCalls)
            assertEquals(listOf("com.x", "com.y"), session.runningApps?.map { it.packageName })
            assertEquals(100, vm.state.value.progress)
            assertFalse(vm.state.value.isBackBlocked)
        }

    /** The ring fills from its own read, well before the beat ends. */
    @Test
    fun `the memory reading reaches the ring`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()

        assertEquals(8_000_000_000L, vm.state.value.memory?.totalBytes)
    }

    /** The badge write gets its own coroutine, so navigation never waits on a disk write (§6.4). */
    @Test
    fun `today is marked as scanned`() = mainDispatcher.runVmTest {
        viewModel()
        settle()

        assertEquals(1, badges.marks)
    }

    /**
     * A failed enumeration writes `null`, which is the session-lost branch `runningapps` already
     * owns — it loads for itself and renders its own error, rather than this screen reporting a
     * failure the user cannot act on.
     */
    @Test
    fun `a failed enumeration still navigates and leaves the store empty`() =
        mainDispatcher.runVmTest {
            runningApps.apps = null
            val vm = viewModel()

            vm.effects.test {
                settle()
                assertEquals(RunningAppsScanEffect.NavigateToRunningApps, awaitItem())
            }

            assertNull(session.runningApps)
            assertTrue(vm.state.value.isFinished)
        }

    /** Crash containment and the stuck state in one. */
    @Test
    fun `a thrown memory read lowers the back block as well as setting the error`() =
        mainDispatcher.runVmTest {
            metrics.throwOnMemory = true
            val vm = viewModel()
            settle()

            assertTrue(vm.state.value.error is AppError.Unexpected)
            assertFalse(vm.state.value.isBackBlocked)
        }

    @Test
    fun `back during the scan asks for the message`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(RunningAppsScanIntent.BackPressed)
            assertEquals(RunningAppsScanEffect.ShowScanInProgressMessage, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the feature open is tracked exactly once`() = mainDispatcher.runVmTest {
        viewModel()
        settle()

        assertEquals(listOf(AnalyticsEvent.FeatureOpened(FeatureId.RunningApps)), analytics.events)
    }
}
