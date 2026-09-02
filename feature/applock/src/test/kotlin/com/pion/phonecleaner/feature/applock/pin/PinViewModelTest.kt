package com.pion.phonecleaner.feature.applock.pin

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.applock.PinLockout
import com.pion.phonecleaner.domain.model.applock.PinMode
import com.pion.phonecleaner.domain.model.applock.PinVerdict
import com.pion.phonecleaner.domain.usecase.SavePinUseCase
import com.pion.phonecleaner.domain.usecase.VerifyPinUseCase
import com.pion.phonecleaner.feature.applock.testing.FakeAppLockPinRepository
import com.pion.phonecleaner.feature.applock.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.applock.testing.runVmTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/** `docs/screens/16-app-lock.md` §2.2 — the behaviour this ViewModel owes. */
internal class PinViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private fun viewModel(
        mode: PinMode,
        pins: FakeAppLockPinRepository = FakeAppLockPinRepository(),
    ) = PinViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PinArgs.MODE to mode.name)),
        verifyPin = VerifyPinUseCase(pins),
        savePin = SavePinUseCase(pins),
        log = AppLogger.NoOp,
    )

    private fun PinViewModel.type(digits: String) =
        digits.forEach { onIntent(PinIntent.DigitPressed(it.digitToInt())) }

    // ── the argument becomes the initial state ─────────────────────────────────────────────────
    @Test
    fun `Change opens on Verify, so the old PIN is proved before a new one is accepted`() {
        assertEquals(PinStep.Verify, viewModel(PinMode.Change).state.value.step)
    }

    @Test
    fun `Set and Verify open on Enter`() {
        assertEquals(PinStep.Enter, viewModel(PinMode.Set).state.value.step)
        assertEquals(PinStep.Enter, viewModel(PinMode.Verify).state.value.step)
    }

    @Test
    fun `an unreadable argument falls back to Verify, the mode that cannot write a PIN`() {
        val vm = PinViewModel(
            savedStateHandle = SavedStateHandle(mapOf(PinArgs.MODE to "not-a-mode")),
            verifyPin = VerifyPinUseCase(FakeAppLockPinRepository()),
            savePin = SavePinUseCase(FakeAppLockPinRepository()),
            log = AppLogger.NoOp,
        )
        assertEquals(PinMode.Verify, vm.state.value.mode)
    }

    // ── the digits never reach the state ───────────────────────────────────────────────────────
    @Test
    fun `state carries a count and nothing else`() {
        val vm = viewModel(PinMode.Verify)
        vm.onIntent(PinIntent.DigitPressed(7))
        vm.onIntent(PinIntent.DigitPressed(7))
        // The whole point: `PinState` has no member that *could* hold a digit, so the count is
        // the only assertion available — which is exactly the assertion the design intends.
        assertEquals(2, vm.state.value.digits)
    }

    @Test
    fun `backspace lowers the count and clears the error`() {
        val vm = viewModel(PinMode.Verify)
        vm.type("12")
        vm.onIntent(PinIntent.BackspacePressed)
        assertEquals(1, vm.state.value.digits)
        assertNull(vm.state.value.errorKind)
    }

    // ── verify ────────────────────────────────────────────────────────────────────────────────
    @Test
    fun `a correct PIN navigates into App Lock and submits exactly the digits entered`() =
        main.runVmTest {
            val pins = FakeAppLockPinRepository(storedPin = "1234")
            val vm = viewModel(PinMode.Verify, pins)
            vm.effects.test {
                vm.type("1234")
                assertIs<PinEffect.NavigateToAppLock>(awaitItem())
                expectNoEvents()
            }
            assertEquals(listOf("1234"), pins.verified)
            assertEquals(0, vm.state.value.digits)
        }

    @Test
    fun `a wrong PIN shakes, counts the attempt and clears the buffer`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = "1234")
        val vm = viewModel(PinMode.Verify, pins)
        vm.effects.test {
            vm.type("0000")
            assertIs<PinEffect.ShakeKeypad>(awaitItem())
            expectNoEvents()
        }
        assertEquals(PinError.WrongPin, vm.state.value.errorKind)
        assertEquals(1, vm.state.value.failedAttempts)
        assertEquals(0, vm.state.value.digits)
    }

    @Test
    fun `a lockout closes the pad and a further digit is dropped`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = "1234")
        pins.nextVerdict = PinVerdict.LockedOut(Instant.fromEpochMilliseconds(9_000L))
        val vm = viewModel(PinMode.Verify, pins)
        vm.type("0000")
        assertEquals(false, vm.state.value.acceptsInput)
        vm.onIntent(PinIntent.DigitPressed(1))
        assertEquals(0, vm.state.value.digits)
        // Only the deadline reopens it; the repository stays the authority on the next attempt.
        vm.onIntent(PinIntent.LockoutElapsed)
        assertEquals(true, vm.state.value.acceptsInput)
    }

    @Test
    fun `NotSet turns the gate into a Set flow instead of showing an error`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = null)
        val vm = viewModel(PinMode.Verify, pins)
        vm.type("1234")
        assertEquals(PinMode.Set, vm.state.value.mode)
        assertEquals(PinStep.Enter, vm.state.value.step)
        assertNull(vm.state.value.errorKind)
    }
}
