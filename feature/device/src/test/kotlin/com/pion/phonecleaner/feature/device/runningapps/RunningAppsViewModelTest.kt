package com.pion.phonecleaner.feature.device.runningapps

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.domain.model.device.UsageAccessState
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.usecase.ListStoppableAppsUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ReadMemoryUseCase
import com.pion.phonecleaner.domain.usecase.ReadUsageAccessUseCase
import com.pion.phonecleaner.domain.usecase.VerifyAppStoppedUseCase
import com.pion.phonecleaner.feature.device.testing.FakeDeviceMetricsRepository
import com.pion.phonecleaner.feature.device.testing.FakeDeviceScanSessionStore
import com.pion.phonecleaner.feature.device.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.device.testing.FakeRunningAppsRepository
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

internal class RunningAppsViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val metrics = FakeDeviceMetricsRepository()
    private val runningApps = FakeRunningAppsRepository()
    private val session = FakeDeviceScanSessionStore()
    private val featureUsage = FakeFeatureUsageRepository()

    private fun viewModel() = RunningAppsViewModel(
        session = session,
        readMemory = ReadMemoryUseCase(metrics),
        listStoppableApps = ListStoppableAppsUseCase(runningApps),
        verifyAppStopped = VerifyAppStoppedUseCase(runningApps),
        readUsageAccess = ReadUsageAccessUseCase(runningApps),
        markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
        log = AppLogger.NoOp,
    )

    /** §6.4: the scan's list is used, and the screen it opens does not enumerate a second time. */
    @Test
    fun `a seeded list is used and the first resume does not enumerate again`() =
        mainDispatcher.runVmTest {
            session.runningApps = persistentListOf(RunningApp("com.seed"))

            val vm = viewModel()
            settle()
            vm.onIntent(RunningAppsIntent.ScreenResumed)
            settle()

            assertEquals(listOf("com.seed"), vm.state.value.apps.map { it.packageName })
            assertEquals(0, runningApps.listCalls)
            assertNull(session.runningApps)
        }

    /** The session-lost branch §0.2 requires: an empty store loads for itself. */
    @Test
    fun `an empty store makes the screen load for itself`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()

        assertEquals(1, runningApps.listCalls)
        assertEquals(listOf("com.a", "com.b"), vm.state.value.apps.map { it.packageName })
        assertFalse(vm.state.value.isRefreshing)
    }

    /** A later resume — the one after the Settings trip — does re-enumerate. */
    @Test
    fun `a later resume refreshes the list`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()
        vm.onIntent(RunningAppsIntent.ScreenResumed)
        settle()
        vm.onIntent(RunningAppsIntent.ScreenResumed)
        settle()

        assertEquals(2, runningApps.listCalls)
    }

    /** Stop opens the sheet and emits **nothing**: the trip starts from the sheet's own action (§7). */
    @Test
    fun `stop raises the sheet and no effect`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()

        vm.effects.test {
            vm.onIntent(RunningAppsIntent.StopTapped("com.a"))
            expectNoEvents()
        }

        assertEquals("com.a", vm.state.value.instructionsFor)
        assertEquals("com.a", vm.state.value.awaitingForceStopOf)
    }

    @Test
    fun `the sheet's primary action closes it and opens the app's settings page`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()
            settle()
            vm.onIntent(RunningAppsIntent.StopTapped("com.a"))

            vm.effects.test {
                vm.onIntent(RunningAppsIntent.InstructionsOpenSettingsTapped)
                assertEquals(RunningAppsEffect.OpenSystemAppInfo("com.a"), awaitItem())
                expectNoEvents()
            }

            assertNull(vm.state.value.instructionsFor)
            // Still pending: this is the package the return trip has to verify.
            assertEquals("com.a", vm.state.value.awaitingForceStopOf)
        }

    /** A user who backs out of the sheet never left the app, so nothing is pending either. */
    @Test
    fun `dismissing the sheet clears the pending package too`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()
        vm.onIntent(RunningAppsIntent.StopTapped("com.a"))

        vm.onIntent(RunningAppsIntent.InstructionsDismissed)

        assertNull(vm.state.value.instructionsFor)
        assertNull(vm.state.value.awaitingForceStopOf)
    }

    /** §6.5: a row is marked stopped **only** when the platform says so. */
    @Test
    fun `a stop android confirms marks that row and only that row`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()
        vm.onIntent(RunningAppsIntent.ScreenResumed)
        settle()
        vm.onIntent(RunningAppsIntent.StopTapped("com.a"))
        vm.onIntent(RunningAppsIntent.InstructionsOpenSettingsTapped)
        runningApps.stopped += "com.a"

        vm.onIntent(RunningAppsIntent.ScreenResumed)
        settle()

        val apps = vm.state.value.apps.associateBy { it.packageName }
        assertTrue(apps.getValue("com.a").isStopped)
        assertFalse(apps.getValue("com.b").isStopped)
        assertNull(vm.state.value.awaitingForceStopOf)
    }

    /** And when the bit is not set, the row stays and we say nothing. */
    @Test
    fun `a stop android does not confirm claims nothing`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()
        vm.onIntent(RunningAppsIntent.ScreenResumed)
        settle()
        vm.onIntent(RunningAppsIntent.StopTapped("com.a"))
        vm.onIntent(RunningAppsIntent.InstructionsOpenSettingsTapped)

        vm.onIntent(RunningAppsIntent.ScreenResumed)
        settle()

        assertTrue(vm.state.value.apps.none { it.isStopped })
        assertNull(vm.state.value.awaitingForceStopOf)
    }

    /** The competitor has no empty state at all — a header, a ring and an empty box (§6.2). */
    @Test
    fun `an empty enumeration is an empty state, not a blank list`() = mainDispatcher.runVmTest {
        runningApps.apps = persistentListOf()

        val vm = viewModel()
        settle()

        assertTrue(vm.state.value.isEmpty)
        assertFalse(vm.state.value.isRefreshing)
    }

    /** A failed enumeration lowers the busy flag — which `isEmpty` also reads — and reports. */
    @Test
    fun `a failed enumeration reports and lowers the busy flag`() = mainDispatcher.runVmTest {
        runningApps.apps = null

        val vm = viewModel()
        settle()

        assertFalse(vm.state.value.isRefreshing)
        assertTrue(vm.state.value.error != null)
    }

    /**
     * PENDING OWNER DECISION 3 (§0.1). The ungranted path is a **rendered** state with a real grant
     * action, and it gates nothing: the list is present either way.
     */
    @Test
    fun `denied usage access renders a rationale that gates nothing`() = mainDispatcher.runVmTest {
        runningApps.access = UsageAccessState.Denied

        val vm = viewModel()
        settle()

        assertTrue(vm.state.value.isUsageAccessVisible)
        assertEquals(2, vm.state.value.apps.size)

        vm.effects.test {
            vm.onIntent(RunningAppsIntent.UsageAccessGrantTapped)
            assertEquals(RunningAppsEffect.OpenUsageAccessSettings, awaitItem())
        }

        vm.onIntent(RunningAppsIntent.UsageAccessDismissed)
        assertFalse(vm.state.value.isUsageAccessVisible)
    }

    @Test
    fun `granted usage access renders no rationale`() = mainDispatcher.runVmTest {
        runningApps.access = UsageAccessState.Granted

        val vm = viewModel()
        settle()

        assertFalse(vm.state.value.isUsageAccessVisible)
    }

    @Test
    fun `opening the screen records the feature as used`() = mainDispatcher.runVmTest {
        viewModel()
        settle()

        assertEquals(listOf(FeatureId.RunningApps), featureUsage.marked)
    }

    @Test
    fun `done and back both navigate back`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        settle()

        vm.effects.test {
            vm.onIntent(RunningAppsIntent.SkipTapped)
            assertEquals(RunningAppsEffect.NavigateBack, awaitItem())

            vm.onIntent(RunningAppsIntent.BackPressed)
            assertEquals(RunningAppsEffect.NavigateBack, awaitItem())
            expectNoEvents()
        }
    }
}
