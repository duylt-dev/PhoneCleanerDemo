package com.pion.phonecleaner.feature.network.traffic

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.network.AppTraffic
import com.pion.phonecleaner.domain.model.network.TrafficFilter
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.domain.model.network.TrafficReport
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.usecase.GetTrafficReportUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.feature.network.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.network.testing.FakeInstalledAppsRepository
import com.pion.phonecleaner.feature.network.testing.FakeNetworkTrafficRepository
import com.pion.phonecleaner.feature.network.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.network.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.network.testing.runVmTest
import com.pion.phonecleaner.feature.network.testing.settle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class NetworkTrafficViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val permissions = FakePermissionRepository()
    private val traffic = FakeNetworkTrafficRepository()
    private val installedApps = FakeInstalledAppsRepository()
    private val featureUsage = FakeFeatureUsageRepository()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) =
        NetworkTrafficViewModel(
            savedState = savedState,
            permissions = permissions,
            getTrafficReport = GetTrafficReportUseCase(traffic),
            installedApps = installedApps,
            markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
            log = AppLogger.NoOp,
        )

    private fun grantUsageAccess() {
        permissions.granted.value = persistentSetOf(AppPermission.UsageStats)
    }

    // ── reducers ────────────────────────────────────────────────────────────────────────────────

    /** The wall is a rendered state, not a separate Activity — and no query is attempted behind it. */
    @Test
    fun `an ungranted usage access lands on the wall and asks for nothing`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()

            vm.onIntent(NetworkTrafficIntent.ScreenStarted)
            settle()

            assertEquals(NetworkTrafficState.Phase.NeedsUsageAccess, vm.state.value.phase)
            assertTrue(traffic.requested.isEmpty())
        }

    /** A UID with no resolvable label is dropped rather than rendered as a raw package id (D4). */
    @Test
    fun `rows carry labels and an unlabelled uid is dropped`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        traffic.answer = AppResult.Success(report())
        installedApps.apps = persistentListOf(installed("a.app", "Alpha"))
        val vm = viewModel()

        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()

        val rows = vm.state.value.rows
        assertEquals(1, rows.size)
        assertEquals("Alpha", rows.single().label)
        assertEquals(300L, rows.single().bytes)
    }

    /** A filter change re-derives the rows; it never re-queries. One query per period. */
    @Test
    fun `selecting a filter re-derives rows without a second query`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        traffic.answer = AppResult.Success(report())
        installedApps.apps = persistentListOf(installed("a.app", "Alpha"))
        val vm = viewModel()
        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()

        vm.onIntent(NetworkTrafficIntent.SelectFilter(TrafficFilter.Wifi))

        assertEquals(1, traffic.requested.size)
        assertEquals(200L, vm.state.value.rows.single().bytes)
    }

    /** A re-tap of the selected period is dropped in the reducer, as the competitor's `m0()` is not. */
    @Test
    fun `re-selecting the current period does not re-query`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        val vm = viewModel()
        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()
        val before = traffic.requested.size

        vm.onIntent(NetworkTrafficIntent.SelectPeriod(TrafficPeriod.ThisMonth))
        settle()

        assertEquals(before, traffic.requested.size)
    }

    /** D6: the period survives process death, so a restored handle does not fall back to the default. */
    @Test
    fun `the period is restored from the saved state handle`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        val handle = SavedStateHandle()
        viewModel(handle).onIntent(NetworkTrafficIntent.SelectPeriod(TrafficPeriod.Last24Hours))
        settle()

        val restored = viewModel(handle)

        assertEquals(TrafficPeriod.Last24Hours, restored.state.value.period)
    }

    // ── effects ─────────────────────────────────────────────────────────────────────────────────

    /** The row is marked and the system page is asked for. Nothing claims to have stopped anything. */
    @Test
    fun `app info marks the row and raises one effect`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        val vm = viewModel()
        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()

        vm.effects.test {
            vm.onIntent(NetworkTrafficIntent.StopPressed("a.app"))

            val effect = awaitItem()
            assertTrue(effect is NetworkTrafficEffect.OpenAppDetails)
            assertEquals("a.app", (effect as NetworkTrafficEffect.OpenAppDetails).packageName)
            expectNoEvents()
        }
        assertEquals("a.app", vm.state.value.stopRequestedFor)
    }

    /** Returning from App info re-queries, which is what stops the list going stale (D5). */
    @Test
    fun `returning after an app info trip re-queries`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        val vm = viewModel()
        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()
        vm.onIntent(NetworkTrafficIntent.StopPressed("a.app"))

        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()

        assertEquals(2, traffic.requested.size)
        assertNull(vm.state.value.stopRequestedFor)
    }

    // ── crash containment and stuck states ──────────────────────────────────────────────────────

    /** A repository that throws lowers the phase and reaches the state as an error, not a crash. */
    @Test
    fun `a throwing repository leaves a ready screen carrying an error`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        traffic.throwOnReport = true
        val vm = viewModel()

        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()

        assertEquals(NetworkTrafficState.Phase.Ready, vm.state.value.phase)
        assertTrue(vm.state.value.error is AppError.Unexpected)
    }

    /** The retry after a failure still reaches the repository — the flag came down, not just the spinner. */
    @Test
    fun `retry after a failure reaches the repository again`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        traffic.throwOnReport = true
        val vm = viewModel()
        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()

        traffic.throwOnReport = false
        vm.onIntent(NetworkTrafficIntent.RetryPressed)
        settle()

        assertEquals(2, traffic.requested.size)
        assertNull(vm.state.value.error)
    }

    /** A query that never answers must still bring the indicator down at the bound. */
    @Test
    fun `a query that never returns times out and leaves the busy state`() =
        mainDispatcher.runVmTest {
            grantUsageAccess()
            traffic.neverReturns = true
            val vm = viewModel()

            vm.onIntent(NetworkTrafficIntent.ScreenStarted)
            settle()

            assertEquals(NetworkTrafficState.Phase.Ready, vm.state.value.phase)
            assertTrue(vm.state.value.error is AppError.Unexpected)
        }

    /** Labels that cannot be read are reported, not swallowed into an empty-looking list. */
    @Test
    fun `a label failure reaches the state as an error`() = mainDispatcher.runVmTest {
        grantUsageAccess()
        installedApps.failure = AppResult.Failure(AppError.PermissionDenied("packages"))
        val vm = viewModel()

        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()

        assertTrue(vm.state.value.error is AppError.PermissionDenied)
        assertEquals(NetworkTrafficState.Phase.Ready, vm.state.value.phase)
    }

    /** Bookkeeping happens once the grant is in hand, and never before it. */
    @Test
    fun `the feature is marked used only once access is granted`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()
        assertTrue(featureUsage.marked.isEmpty())

        grantUsageAccess()
        vm.onIntent(NetworkTrafficIntent.ScreenStarted)
        settle()

        assertEquals(listOf(FeatureId.NetworkTraffic), featureUsage.marked)
    }

    private fun report() = TrafficReport(
        period = TrafficPeriod.ThisMonth,
        mobileBytes = 100L,
        wifiBytes = 200L,
        apps = persistentListOf(
            AppTraffic(uid = 10, packageNames = persistentListOf("a.app"), mobileBytes = 100L, wifiBytes = 200L),
            AppTraffic(uid = 11, packageNames = persistentListOf("gone.app"), mobileBytes = 50L, wifiBytes = 0L),
        ),
    )

    private fun installed(packageName: String, label: String) = InstalledApp(
        packageName = packageName,
        label = label,
        uid = 10,
        apkBytes = 0L,
    )
}
