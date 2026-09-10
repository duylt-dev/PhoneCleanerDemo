package com.pion.phonecleaner.feature.files.appmanager

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.app.AppStorageStats
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.usecase.LoadInstalledAppsUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.UninstallAppUseCase
import com.pion.phonecleaner.feature.files.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.files.testing.FakeAppControlRepository
import com.pion.phonecleaner.feature.files.testing.FakeAppStorageStatsRepository
import com.pion.phonecleaner.feature.files.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.files.testing.FakeInstalledAppsRepository
import com.pion.phonecleaner.feature.files.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class AppManagerViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val installedApps = FakeInstalledAppsRepository(
        apps = listOf(app("one", 10L), app("two", 20L)).toImmutableList(),
    )
    private val stats = FakeAppStorageStatsRepository()
    private val appControl = FakeAppControlRepository(mutableSetOf("one", "two"))
    private val permissions = FakePermissionRepository()
    private val analytics = FakeAnalyticsRepository()

    private fun viewModel() = AppManagerViewModel(
        savedState = SavedStateHandle(),
        loadInstalledApps = LoadInstalledAppsUseCase(installedApps, stats),
        uninstallApp = UninstallAppUseCase(appControl),
        permissions = permissions,
        markFeatureUsed = MarkFeatureUsedUseCase(FakeFeatureUsageRepository()),
        analytics = analytics,
        log = AppLogger.NoOp,
    )

    /** The screen opens without `PACKAGE_USAGE_STATS`; only one column and one chip depend on it. */
    @Test
    fun `the list loads with usage access denied and the last-used chip is disabled`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()

            vm.onIntent(AppManagerIntent.ScreenStarted)
            settle()

            assertEquals(UsageAccess.Denied, vm.state.value.usageAccess)
            assertEquals(2, vm.state.value.apps.size)
            assertFalse(vm.state.value.lastUsedSortEnabled)
        }

    /** Sizes stream in after the list is already on screen (§5.2). */
    @Test
    fun `a size arriving late replaces its row and does not disturb the others`() =
        mainDispatcher.runVmTest {
            stats.stats = listOf(AppStorageStats("one", appBytes = 5L, dataBytes = 1L))
            val vm = viewModel()

            vm.onIntent(AppManagerIntent.ScreenStarted)
            settle()

            val measured = vm.state.value.apps.first { it.packageName == "one" }
            assertEquals(6L, measured.totalBytes)
            assertTrue(measured.sizeKnown)
            assertFalse(vm.state.value.apps.first { it.packageName == "two" }.sizeKnown)
            assertEquals(1, vm.state.value.sizedCount)
        }

    /** A re-tap flips the direction; a different key lands descending. Stated, not accidental. */
    @Test
    fun `re-tapping the active sort key flips the direction`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(AppManagerIntent.ScreenStarted)
        settle()

        vm.onIntent(AppManagerIntent.SortSelected(AppSortKey.Size))
        assertEquals(AppSort(AppSortKey.Size, descending = false), vm.state.value.sort)

        vm.onIntent(AppManagerIntent.SortSelected(AppSortKey.InstallDate))
        assertEquals(AppSort(AppSortKey.InstallDate, descending = true), vm.state.value.sort)
    }

    /** The *Last used* sort is refused without the grant instead of sorting a column of zeroes. */
    @Test
    fun `the last-used sort is ignored while usage access is denied`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(AppManagerIntent.ScreenStarted)
        settle()

        vm.onIntent(AppManagerIntent.SortSelected(AppSortKey.LastUsed))

        assertEquals(AppSortKey.Size, vm.state.value.sort.key)
    }

    /**
     * One dialog at a time, and the outcome comes from a **re-query**: `two` is still installed when
     * its round trip returns, so it is declined rather than counted as removed (§5.5).
     */
    @Test
    fun `the uninstall queue asks one at a time and counts only a verified removal`() =
        mainDispatcher.runVmTest {
            permissions.granted.value = persistentSetOf(AppPermission.UsageStats)
            stats.stats = listOf(AppStorageStats("one", appBytes = 40L))
            val vm = viewModel()
            vm.onIntent(AppManagerIntent.ScreenStarted)
            settle()
            vm.onIntent(AppManagerIntent.CompletionAnimationFinished)
            vm.onIntent(AppManagerIntent.RowToggled("one"))
            vm.onIntent(AppManagerIntent.RowToggled("two"))

            vm.effects.test {
                vm.onIntent(AppManagerIntent.UninstallPressed)
                vm.onIntent(AppManagerIntent.UninstallConfirmed)
                settle()

                assertEquals(AppManagerEffect.RequestUninstall("one"), awaitItem())

                // The user went through with this one.
                appControl.installed.remove("one")
                vm.onIntent(AppManagerIntent.UninstallReturned("one"))
                settle()
                assertEquals(AppManagerEffect.RequestUninstall("two"), awaitItem())

                // ... and cancelled this one. `declined` is first class; the run still finishes.
                vm.onIntent(AppManagerIntent.UninstallReturned("two"))
                settle()

                val finished = awaitItem()
                assertTrue(finished is AppManagerEffect.NavigateToCleanResult)
                val summary = (finished as AppManagerEffect.NavigateToCleanResult).summary
                assertEquals(1, summary.itemCount)
                assertEquals(40L, summary.freedBytes)
            }

            assertNull(vm.state.value.uninstalling)
            assertEquals(listOf("two"), vm.state.value.apps.map { it.packageName })
            assertEquals(ToolPhase.Ready, vm.state.value.phase)
        }

    /**
     * Confirming our dialog and then declining Android's is a **cancel**, not a finished run. The
     * screen stays where it is: navigating to the shared result screen would render `NothingFound`
     * — *"Nothing was found to remove"* — over a list where the apps were found and are still there.
     */
    @Test
    fun `declining every system dialog does not reach the result screen`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()
            vm.onIntent(AppManagerIntent.ScreenStarted)
            settle()
            vm.onIntent(AppManagerIntent.CompletionAnimationFinished)
            vm.onIntent(AppManagerIntent.RowToggled("one"))

            vm.effects.test {
                vm.onIntent(AppManagerIntent.UninstallPressed)
                vm.onIntent(AppManagerIntent.UninstallConfirmed)
                settle()
                assertEquals(AppManagerEffect.RequestUninstall("one"), awaitItem())

                // The user backed out of the system sheet: `one` is still installed.
                vm.onIntent(AppManagerIntent.UninstallReturned("one"))
                settle()

                expectNoEvents()
            }

            // The run is over and the row is still both listed and ticked, so the retry is one tap.
            assertNull(vm.state.value.uninstalling)
            assertEquals(ToolPhase.Ready, vm.state.value.phase)
            assertEquals(listOf("one", "two"), vm.state.value.apps.map { it.packageName })
            assertEquals(persistentSetOf("one"), vm.state.value.selectedPackages)
        }

    /** The rationale is shown BEFORE the user is handed out to Settings (§5.5). */
    @Test
    fun `the grant press opens a rationale first and Settings only on continue`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()
            vm.onIntent(AppManagerIntent.ScreenStarted)
            settle()

            vm.effects.test {
                vm.onIntent(AppManagerIntent.GrantUsageAccessPressed)
                assertTrue(vm.state.value.rationale)
                expectNoEvents()

                vm.onIntent(AppManagerIntent.RationaleContinued)
                settle()
                assertEquals(AppManagerEffect.OpenUsageAccessSettings, awaitItem())
                assertFalse(vm.state.value.rationale)
            }
        }

    /**
     * Both dates survive the port -> use case -> row hop as themselves. They used to not: the row's
     * install date was hard-coded to `0` in the use case, so every row rendered "install date not
     * available" no matter what the platform reported.
     */
    @Test
    fun `the install and last-used timestamps reach the row`() = mainDispatcher.runVmTest {
        installedApps.apps = listOf(
            app("one", apkBytes = 10L, lastUsedAtMillis = 1_700L, firstInstallAtMillis = 900L),
            app("two", apkBytes = 20L),
        ).toImmutableList()
        val vm = viewModel()

        vm.onIntent(AppManagerIntent.ScreenStarted)
        settle()

        val one = vm.state.value.apps.first { it.packageName == "one" }
        assertEquals(900L, one.firstInstallEpochMillis)
        assertEquals(1_700L, one.lastUsedEpochMillis)

        // `0` is carried through as `0`, never as a date the row would then render.
        val two = vm.state.value.apps.first { it.packageName == "two" }
        assertEquals(0L, two.firstInstallEpochMillis)
        assertEquals(0L, two.lastUsedEpochMillis)
    }

    /** The row needs the grant to know which of the two meanings a `0` last-used stamp carries. */
    @Test
    fun `granting usage access flips the flag the row reads`() = mainDispatcher.runVmTest {
        val vm = viewModel()
        vm.onIntent(AppManagerIntent.ScreenStarted)
        settle()
        assertFalse(vm.state.value.usageAccessGranted)

        permissions.granted.value = persistentSetOf(AppPermission.UsageStats)
        vm.onIntent(AppManagerIntent.ScreenStarted)
        settle()

        assertTrue(vm.state.value.usageAccessGranted)
    }

    /**
     * Owner decision (2026-09-03): a system app is never listed, and no switch shows it. The port
     * still returns them — the filter is `LoadInstalledAppsUseCase`'s, and this asserts the screen
     * sees the filtered list, not that the port was asked for one.
     */
    @Test
    fun `a system app never reaches the list and is never sized`() = mainDispatcher.runVmTest {
        installedApps.apps = listOf(
            app("one", 10L),
            app("system", 30L, isSystem = true),
        ).toImmutableList()
        // The fan-out would report it if it were asked; the row must still be absent.
        stats.stats = listOf(AppStorageStats("system", appBytes = 5L, dataBytes = 1L))
        val vm = viewModel()

        vm.onIntent(AppManagerIntent.ScreenStarted)
        settle()

        assertEquals(listOf("one"), vm.state.value.apps.map { it.packageName })
    }

    private companion object {
        fun app(
            packageName: String,
            apkBytes: Long,
            lastUsedAtMillis: Long = 0L,
            firstInstallAtMillis: Long = 0L,
            isSystem: Boolean = false,
        ) = InstalledApp(
            packageName = packageName,
            label = packageName,
            uid = packageName.hashCode(),
            apkBytes = apkBytes,
            lastUsedAtMillis = lastUsedAtMillis,
            firstInstallAtMillis = firstInstallAtMillis,
            isSystem = isSystem,
        )
    }
}
