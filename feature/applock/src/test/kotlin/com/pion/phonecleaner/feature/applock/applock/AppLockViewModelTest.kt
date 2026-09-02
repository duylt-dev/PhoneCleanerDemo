package com.pion.phonecleaner.feature.applock.applock

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ObserveAppLockSettingsUseCase
import com.pion.phonecleaner.domain.usecase.ObserveLockableAppsUseCase
import com.pion.phonecleaner.domain.usecase.SetAppLockedUseCase
import com.pion.phonecleaner.domain.usecase.SetLockNewlyInstalledUseCase
import com.pion.phonecleaner.feature.applock.testing.FakeAppLockRepository
import com.pion.phonecleaner.feature.applock.testing.FakeAppLockSettingsRepository
import com.pion.phonecleaner.feature.applock.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.applock.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.applock.testing.lockableApps
import com.pion.phonecleaner.feature.applock.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** `docs/screens/16-app-lock.md` §1.2 — the behaviour this ViewModel owes. */
internal class AppLockViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val lockList = FakeAppLockRepository(lockableApps())
    private val settings = FakeAppLockSettingsRepository()
    private val featureUsage = FakeFeatureUsageRepository()

    private fun viewModel() = AppLockViewModel(
        observeLockableApps = ObserveLockableAppsUseCase(lockList),
        setAppLocked = SetAppLockedUseCase(lockList),
        observeAppLockSettings = ObserveAppLockSettingsUseCase(settings),
        setLockNewlyInstalled = SetLockNewlyInstalledUseCase(settings),
        markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
        log = AppLogger.NoOp,
    )

    /** Both permissions granted: every toggle path below is gated on them. */
    private fun AppLockViewModel.granted() =
        onIntent(AppLockIntent.PermissionsResolved(overlay = true, usageStats = true))

    @Test
    fun `the list exists before the scan stage ends`() = main.runVmTest {
        val vm = viewModel()
        // The stage is a presentation stage with its own exit condition, not `isLoading`: the rows
        // are already in state while it is still showing. The competitor renders its list only from
        // a Lottie `onAnimationEnd`, so no animation means no tabs, ever (§1.5).
        assertTrue(vm.state.value.isScanning)
        assertEquals(2, vm.state.value.apps.size)
        vm.onIntent(AppLockIntent.ScanAnimationFinished)
        assertFalse(vm.state.value.isScanning)
    }

    @Test
    fun `entering the home stamps App Lock's own usage key`() = main.runVmTest {
        viewModel()
        // The competitor stamps the Permission Manager's key from this screen, so two features share
        // one "last used" timestamp (§1.5).
        assertEquals(listOf(FeatureId.AppLock), featureUsage.marked)
    }

    @Test
    fun `a row tap without both permissions opens the sheet and writes nothing`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(AppLockIntent.AppRowTapped("com.a"))
        assertTrue(vm.state.value.isPermissionSheetVisible)
        assertEquals(emptyList(), lockList.writes)
    }

    @Test
    fun `locking does not ask, unlocking does`() = main.runVmTest {
        val vm = viewModel()
        vm.granted()

        // com.a is unlocked: the tap writes straight through.
        vm.onIntent(AppLockIntent.AppRowTapped("com.a"))
        assertEquals(listOf("com.a" to true), lockList.writes)
        assertNull(vm.state.value.pendingUnlockPackage)

        // com.b is locked: the tap asks first, because the risk is asymmetric — unlocking silently
        // removes a guard the user thought was there.
        vm.onIntent(AppLockIntent.AppRowTapped("com.b"))
        assertEquals("com.b", vm.state.value.pendingUnlockPackage)
        assertEquals(listOf("com.a" to true), lockList.writes)
    }

    @Test
    fun `confirming the unlock writes it and closes the sheet`() = main.runVmTest {
        val vm = viewModel()
        vm.granted()
        vm.onIntent(AppLockIntent.AppRowTapped("com.b"))
        vm.onIntent(AppLockIntent.UnlockConfirmed)
        assertEquals(listOf("com.b" to false), lockList.writes)
        assertNull(vm.state.value.pendingUnlockPackage)
    }

    @Test
    fun `dismissing the unlock writes nothing`() = main.runVmTest {
        val vm = viewModel()
        vm.granted()
        vm.onIntent(AppLockIntent.AppRowTapped("com.b"))
        vm.onIntent(AppLockIntent.UnlockDismissed)
        assertNull(vm.state.value.pendingUnlockPackage)
        assertEquals(emptyList(), lockList.writes)
    }

    @Test
    fun `the toggle guard is per row and is lowered on success`() = main.runVmTest {
        val vm = viewModel()
        vm.granted()
        vm.onIntent(AppLockIntent.AppRowTapped("com.a"))
        // Lowered on the Success arm, so a second tap on the same row is possible again — and no
        // OTHER row was ever blocked. The competitor de-duplicates with a process-global 500 ms
        // click debounce, under which a tap anywhere blocks a tap everywhere.
        assertTrue(vm.state.value.togglingPackages.isEmpty())
        assertEquals(1, lockList.writes.size)
    }

    @Test
    fun `a failed write lowers the guard and surfaces the error`() = main.runVmTest {
        lockList.failure = AppError.Storage(cause = "datastore")
        val vm = viewModel()
        vm.granted()
        vm.effects.test {
            vm.onIntent(AppLockIntent.AppRowTapped("com.a"))
            assertIs<AppLockEffect.ShowMessage>(awaitItem())
        }
        // MVI §1: onError must lower every flag the call raised.
        assertTrue(vm.state.value.togglingPackages.isEmpty())
        // The row still renders from the persisted list, so it did not move.
        assertFalse(vm.state.value.apps.first { it.packageName == "com.a" }.isLocked)
    }

    @Test
    fun `the locked tab and its count read the reconciled list`() = main.runVmTest {
        val vm = viewModel()
        vm.granted()
        assertEquals(1, vm.state.value.lockedCount)
        vm.onIntent(AppLockIntent.TabSelected(AppLockTab.Locked))
        assertEquals(listOf("com.b"), vm.state.value.visibleApps.map { it.packageName })
        // The repository re-emits after the write, so the count follows without a second signal —
        // the competitor needs a third LiveData to say that one row moved (§1.1).
        vm.onIntent(AppLockIntent.AppRowTapped("com.a"))
        assertEquals(2, vm.state.value.lockedCount)
    }

    @Test
    fun `a sheet asking for a grant the user has just given closes itself`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(AppLockIntent.AppRowTapped("com.a"))
        assertTrue(vm.state.value.isPermissionSheetVisible)
        // There is no grant callback for either special access, so the ON_START re-read is the only
        // signal there is. A partial grant leaves the sheet up.
        vm.onIntent(AppLockIntent.PermissionsResolved(overlay = true, usageStats = false))
        assertTrue(vm.state.value.isPermissionSheetVisible)
        vm.granted()
        assertFalse(vm.state.value.isPermissionSheetVisible)
    }

    @Test
    fun `both grant taps are Effects, because the ViewModel never builds an Intent`() =
        main.runVmTest {
            val vm = viewModel()
            vm.effects.test {
                vm.onIntent(AppLockIntent.GrantUsageStatsTapped)
                assertIs<AppLockEffect.RequestUsageStatsPermission>(awaitItem())
                vm.onIntent(AppLockIntent.GrantOverlayTapped)
                assertIs<AppLockEffect.RequestOverlayPermission>(awaitItem())
            }
        }

    @Test
    fun `navigation is an Effect, never a flag on state`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(AppLockIntent.SettingsTapped)
            assertIs<AppLockEffect.NavigateToSettings>(awaitItem())
            vm.onIntent(AppLockIntent.BackPressed)
            assertIs<AppLockEffect.NavigateBack>(awaitItem())
        }
    }

    @Test
    fun `the newly-installed switch renders from the store, not from the tap`() = main.runVmTest {
        settings.failure = AppError.Storage(cause = "datastore")
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(AppLockIntent.LockNewlyInstalledChanged(false))
            assertIs<AppLockEffect.ShowMessage>(awaitItem())
        }
        assertTrue(vm.state.value.lockNewlyInstalled)
    }
}
