package com.pion.phonecleaner.feature.notification.permissionmanager

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.permission.PermissionGroupId
import com.pion.phonecleaner.domain.usecase.GroupAppsByPermissionUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.RefreshAppPermissionsUseCase
import com.pion.phonecleaner.domain.usecase.ScanAppPermissionsUseCase
import com.pion.phonecleaner.feature.notification.testing.FakeAppPermissionScanRepository
import com.pion.phonecleaner.feature.notification.testing.FakeFeatureStatsRepository
import com.pion.phonecleaner.feature.notification.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.notification.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.notification.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.notification.testing.report
import com.pion.phonecleaner.feature.notification.testing.runVmTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** `docs/screens/17-notification-and-permissions.md` §4.2 and the deltas of §4.5. */
internal class PermissionManagerViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val scan = FakeAppPermissionScanRepository(
        listOf(
            report("com.camera", listOf("android.permission.CAMERA")),
            report("com.quiet"),
        ),
    )
    private val permissions = FakePermissionRepository(setOf(AppPermission.UsageStats))
    private val stats = FakeFeatureStatsRepository()
    private val usage = FakeFeatureUsageRepository()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) = PermissionManagerViewModel(
        savedStateHandle = handle,
        scanPermissions = ScanAppPermissionsUseCase(scan),
        refreshApp = RefreshAppPermissionsUseCase(scan),
        groupApps = GroupAppsByPermissionUseCase(),
        permissions = permissions,
        featureStats = stats,
        markFeatureUsed = MarkFeatureUsedUseCase(usage),
    )

    @Test
    fun `the tab route argument is read at construction`() = dispatcherRule.runVmTest {
        val handle = SavedStateHandle(mapOf(TAB_KEY to PermissionTab.SpecialAccess.name))

        assertEquals(PermissionTab.SpecialAccess, viewModel(handle).state.value.selectedTab)
    }

    @Test
    fun `an unreadable tab argument lands on the first tab, never on a crash`() =
        dispatcherRule.runVmTest {
            val handle = SavedStateHandle(mapOf(TAB_KEY to "not-a-tab"))

            assertEquals(PermissionTab.Apps, viewModel(handle).state.value.selectedTab)
        }

    @Test
    fun `apps and groups are written together`() = dispatcherRule.runVmTest {
        val state = viewModel().state.value

        assertEquals(2, state.apps.size)
        // Every group is returned by PermissionGrouping; the SCREEN filters the empty ones.
        assertEquals(PermissionGroupId.entries.size, state.groups.size)
        assertEquals(listOf(PermissionGroupId.CAMERA), state.visibleGroups.map { it.id })
    }

    @Test
    fun `the sensitive count is written at the end of the scan, not in a destroy callback`() =
        dispatcherRule.runVmTest {
            viewModel()

            assertEquals(listOf(1), stats.counts)
        }

    @Test
    fun `special access carries its grant state`() = dispatcherRule.runVmTest {
        val rows = viewModel().state.value.specialAccess

        assertEquals(6, rows.size)
        assertTrue(rows.single { it.access == AppPermission.UsageStats }.isGranted)
        assertTrue(!rows.single { it.access == AppPermission.Overlay }.isGranted)
    }

    @Test
    fun `the scan animation is the only thing that lowers isScanning`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isScanning)

        viewModel.onIntent(PermissionManagerIntent.ScanAnimationFinished)

        assertTrue(!viewModel.state.value.isScanning)
    }

    @Test
    fun `a scan failure lowers isScanning too`() = dispatcherRule.runVmTest {
        scan.scanError = AppError.PermissionDenied()

        val viewModel = viewModel()

        assertTrue(!viewModel.state.value.isScanning)
        assertTrue(viewModel.state.value.error != null)
    }

    @Test
    fun `the sheet holds an id and reads through apps`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()

        viewModel.onIntent(PermissionManagerIntent.AppRowTapped("com.camera"))

        assertEquals("com.camera", viewModel.state.value.detailSheet?.packageName)
        assertEquals("com.camera", viewModel.state.value.detailApp?.packageName)
    }

    @Test
    fun `manage marks the pending refresh and the next resume spends it`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()
        viewModel.onIntent(PermissionManagerIntent.AppRowTapped("com.camera"))

        viewModel.onIntent(PermissionManagerIntent.ManageTapped)
        assertEquals("com.camera", viewModel.state.value.awaitingRefreshFor)
        assertEquals(
            PermissionManagerEffect.OpenAppSettings("com.camera"),
            viewModel.effects.first(),
        )

        // The refresh returns null: the app now holds nothing, so the row goes and the sheet with it.
        scan.refreshResult = null
        viewModel.onIntent(PermissionManagerIntent.ScreenResumed)

        assertEquals(listOf("com.camera"), scan.refreshed)
        assertEquals(null, viewModel.state.value.awaitingRefreshFor)
        assertEquals(listOf("com.quiet"), viewModel.state.value.apps.map { it.packageName })
        assertEquals(null, viewModel.state.value.detailSheet)
        // groups is recomputed from the new list in the same write (§4.5).
        assertTrue(viewModel.state.value.visibleGroups.isEmpty())
    }

    @Test
    fun `resume re-reads the six grants even with nothing pending`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()
        permissions.granted = setOf(AppPermission.Overlay)

        viewModel.onIntent(PermissionManagerIntent.ScreenResumed)

        val rows = viewModel.state.value.specialAccess
        assertTrue(rows.single { it.access == AppPermission.Overlay }.isGranted)
        assertTrue(!rows.single { it.access == AppPermission.UsageStats }.isGranted)
        assertTrue(scan.refreshed.isEmpty())
    }

    @Test
    fun `group expansion survives a data refresh`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()
        viewModel.onIntent(PermissionManagerIntent.GroupToggled(PermissionGroupId.CAMERA))

        viewModel.onIntent(PermissionManagerIntent.ScreenResumed)

        assertTrue(PermissionGroupId.CAMERA in viewModel.state.value.expandedGroups)
    }

    @Test
    fun `back is blocked while scanning`() = dispatcherRule.runVmTest {
        val viewModel = viewModel()

        viewModel.onIntent(PermissionManagerIntent.BackPressed)

        assertEquals(PermissionManagerEffect.ShowScanInProgressMessage, viewModel.effects.first())
    }
}
