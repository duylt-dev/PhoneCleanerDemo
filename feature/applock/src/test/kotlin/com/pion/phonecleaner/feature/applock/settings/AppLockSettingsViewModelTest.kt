package com.pion.phonecleaner.feature.applock.settings

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.applock.AppLockSettings
import com.pion.phonecleaner.domain.usecase.ClearAppLockUseCase
import com.pion.phonecleaner.domain.usecase.ObserveAppLockSettingsUseCase
import com.pion.phonecleaner.domain.usecase.SetAppLockEnabledUseCase
import com.pion.phonecleaner.domain.usecase.SetLockNewlyInstalledUseCase
import com.pion.phonecleaner.feature.applock.testing.FakeAppLockPinRepository
import com.pion.phonecleaner.feature.applock.testing.FakeAppLockRepository
import com.pion.phonecleaner.feature.applock.testing.FakeAppLockSettingsRepository
import com.pion.phonecleaner.feature.applock.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.applock.testing.lockableApps
import com.pion.phonecleaner.feature.applock.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** `docs/screens/16-app-lock.md` §4.2 — the behaviour this ViewModel owes. */
internal class AppLockSettingsViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val pins = FakeAppLockPinRepository()
    private val lockList = FakeAppLockRepository(lockableApps())
    private val settings = FakeAppLockSettingsRepository()

    private fun viewModel() = AppLockSettingsViewModel(
        observeAppLockSettings = ObserveAppLockSettingsUseCase(settings),
        setAppLockEnabled = SetAppLockEnabledUseCase(settings),
        setLockNewlyInstalled = SetLockNewlyInstalledUseCase(settings),
        clearAppLock = ClearAppLockUseCase(pins, lockList),
        log = AppLogger.NoOp,
    )

    @Test
    fun `both switches render from one persisted upstream`() = main.runVmTest {
        settings.setAppLockEnabled(false)
        val vm = viewModel()
        assertEquals(false, vm.state.value.isAppLockEnabled)
        assertEquals(true, vm.state.value.lockNewlyInstalled)
    }

    @Test
    fun `the master switch writes the flag and nothing else`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(AppLockSettingsIntent.AppLockEnabledChanged(false))
        // The ViewModel never starts or stops the monitor: the repository owns that, driven off the
        // persisted flag. `MajimatActivity.java:37-44` calls od.e0.d()/g() from a click listener.
        assertEquals(listOf("enabled" to false), settings.writes)
        assertEquals(false, vm.state.value.isAppLockEnabled)
    }

    @Test
    fun `a failed write leaves the switch where the store left it`() = main.runVmTest {
        settings.failure = AppError.Storage(cause = "datastore")
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(AppLockSettingsIntent.LockNewlyInstalledChanged(false))
            assertIs<AppLockSettingsEffect.ShowMessage>(awaitItem())
        }
        assertEquals(true, vm.state.value.lockNewlyInstalled)
    }

    @Test
    fun `Change PIN is a navigation Effect, never a flag on state`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(AppLockSettingsIntent.ChangePasswordTapped)
            assertIs<AppLockSettingsEffect.NavigateToChangePin>(awaitItem())
        }
    }

    @Test
    fun `clearing asks first, and the dialog is state so a rotation keeps it`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(AppLockSettingsIntent.ClearAppLockTapped)
        assertEquals(AppLockSettingsDialog.ClearAppLock, vm.state.value.dialog)
        // Nothing has run yet: the confirmation is a question, not a delayed action.
        assertEquals(0, pins.clearCount)
        vm.onIntent(AppLockSettingsIntent.ClearAppLockDismissed)
        assertNull(vm.state.value.dialog)
        assertEquals(0, pins.clearCount)
    }

    @Test
    fun `confirming wipes the PIN and the lock list together`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(AppLockSettingsIntent.ClearAppLockTapped)
        vm.onIntent(AppLockSettingsIntent.ClearAppLockConfirmed)
        assertEquals(1, pins.clearCount)
        assertEquals(1, lockList.clearListCount)
        assertNull(vm.state.value.dialog)
        assertEquals(false, vm.state.value.isClearing)
    }

    @Test
    fun `a failed clear lowers isClearing and surfaces the error`() = main.runVmTest {
        pins.failure = AppError.Storage(cause = "keystore")
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(AppLockSettingsIntent.ClearAppLockConfirmed)
            assertIs<AppLockSettingsEffect.ShowMessage>(awaitItem())
        }
        assertEquals(false, vm.state.value.isClearing)
        // The PIN wipe failed, so the list must NOT have been emptied behind it.
        assertEquals(0, lockList.clearListCount)
    }

    @Test
    fun `defaults are both true, for parity with the competitor's od d0 defaults`() {
        val vm = AppLockSettingsViewModel(
            observeAppLockSettings = ObserveAppLockSettingsUseCase(
                FakeAppLockSettingsRepository(AppLockSettings()),
            ),
            setAppLockEnabled = SetAppLockEnabledUseCase(settings),
            setLockNewlyInstalled = SetLockNewlyInstalledUseCase(settings),
            clearAppLock = ClearAppLockUseCase(pins, lockList),
            log = AppLogger.NoOp,
        )
        assertEquals(true, vm.state.value.isAppLockEnabled)
        assertEquals(true, vm.state.value.lockNewlyInstalled)
    }
}
