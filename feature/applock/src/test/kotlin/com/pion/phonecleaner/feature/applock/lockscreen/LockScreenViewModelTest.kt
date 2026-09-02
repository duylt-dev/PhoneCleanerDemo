package com.pion.phonecleaner.feature.applock.lockscreen

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.applock.PinVerdict
import com.pion.phonecleaner.domain.usecase.VerifyPinUseCase
import com.pion.phonecleaner.feature.applock.pin.PinError
import com.pion.phonecleaner.feature.applock.testing.FakeAppLockPinRepository
import com.pion.phonecleaner.feature.applock.testing.FakeForegroundAppMonitor
import com.pion.phonecleaner.feature.applock.testing.FakeInstalledAppsRepository
import com.pion.phonecleaner.feature.applock.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.applock.testing.installedApp
import com.pion.phonecleaner.feature.applock.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

/** `docs/screens/16-app-lock.md` §3.2 — the behaviour this ViewModel owes. */
internal class LockScreenViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val monitor = FakeForegroundAppMonitor()

    private fun viewModel(
        targetPackage: String? = "com.target",
        pins: FakeAppLockPinRepository = FakeAppLockPinRepository(storedPin = "1234"),
        installed: FakeInstalledAppsRepository = FakeInstalledAppsRepository(
            listOf(installedApp("com.target", "Target")),
        ),
    ) = LockScreenViewModel(
        savedStateHandle = SavedStateHandle(
            if (targetPackage == null) emptyMap() else mapOf(LockScreenArgs.PACKAGE_NAME to targetPackage),
        ),
        verifyPin = VerifyPinUseCase(pins),
        installedApps = installed,
        foregroundAppMonitor = monitor,
        log = AppLogger.NoOp,
    )

    private fun LockScreenViewModel.type(digits: String) =
        digits.forEach { onIntent(LockScreenIntent.DigitPressed(it.digitToInt())) }

    @Test
    fun `the label is resolved from the package, and the icon never reaches this class`() =
        main.runVmTest {
            val vm = viewModel()
            assertEquals("com.target", vm.state.value.targetPackage)
            assertEquals("Target", vm.state.value.targetLabel)
        }

    @Test
    fun `a missing extra is a renderable state, not a crash`() = main.runVmTest {
        val vm = viewModel(targetPackage = null)
        assertEquals("", vm.state.value.targetPackage)
        assertEquals("", vm.state.value.targetLabel)
        // The prompt still has to appear: the alternative is an unlocked app.
        assertEquals(true, vm.state.value.acceptsInput)
    }

    @Test
    fun `an unresolvable package leaves the label empty and still accepts a PIN`() =
        main.runVmTest {
            val vm = viewModel(installed = FakeInstalledAppsRepository(emptyList()))
            assertEquals("", vm.state.value.targetLabel)
            assertEquals(true, vm.state.value.acceptsInput)
        }

    @Test
    fun `a correct PIN records the unlock BEFORE it emits Unlock`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            vm.type("1234")
            // The record is in by the time the Effect arrives, so a monitor restart racing the
            // dismissal cannot re-lock behind it — the competitor's "unlocked" is a side effect of
            // one static dedup field instead.
            assertEquals(listOf("com.target"), monitor.unlocked)
            assertIs<LockScreenEffect.Unlock>(awaitItem())
        }
    }

    @Test
    fun `a wrong PIN shakes and never records an unlock`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            vm.type("0000")
            assertIs<LockScreenEffect.ShakeKeypad>(awaitItem())
        }
        assertEquals(emptyList(), monitor.unlocked)
        assertEquals(PinError.WrongPin, vm.state.value.errorKind)
        assertEquals(0, vm.state.value.digits)
    }

    @Test
    fun `the lockout is the repository's, shared with the pin screen, and closes the pad`() =
        main.runVmTest {
            val pins = FakeAppLockPinRepository(storedPin = "1234")
            pins.nextVerdict = PinVerdict.LockedOut(Instant.fromEpochMilliseconds(60_000L))
            val vm = viewModel(pins = pins)
            vm.type("0000")
            assertEquals(false, vm.state.value.acceptsInput)
            vm.onIntent(LockScreenIntent.DigitPressed(9))
            assertEquals(0, vm.state.value.digits)
        }

    @Test
    fun `back goes to the launcher as an Effect, never into the guarded app`() = main.runVmTest {
        val vm = viewModel()
        vm.effects.test {
            vm.onIntent(LockScreenIntent.BackPressed)
            assertIs<LockScreenEffect.GoHome>(awaitItem())
        }
        assertEquals(emptyList(), monitor.unlocked)
    }

    @Test
    fun `a repository failure is a refused attempt, never an unlock`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = "1234")
        pins.failure = AppError.Storage(cause = "keystore")
        val vm = viewModel(pins = pins)
        vm.effects.test {
            vm.type("1234")
            assertIs<LockScreenEffect.ShakeKeypad>(awaitItem())
        }
        assertEquals(emptyList(), monitor.unlocked)
        assertEquals(false, vm.state.value.isSubmitting)
    }

    @Test
    fun `NotSet unlocks rather than stranding the user in front of their own app`() =
        main.runVmTest {
            val vm = viewModel(pins = FakeAppLockPinRepository(storedPin = null))
            vm.effects.test {
                vm.type("1234")
                assertIs<LockScreenEffect.Unlock>(awaitItem())
            }
        }
}
