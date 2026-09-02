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
import kotlin.time.Instant

/**
 * The two-entry half of `docs/screens/16-app-lock.md` §2.2 — `Set`, `Change`, back and the failure
 * arm. Split from `PinViewModelTest` only to keep both files under the 200-line rule (`LLM.md` §4).
 */
internal class PinWriteFlowTest {

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

    // ── set and change ────────────────────────────────────────────────────────────────────────
    @Test
    fun `Set stores the PIN only after the two entries match`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = null)
        val vm = viewModel(PinMode.Set, pins)
        vm.effects.test {
            vm.type("4321")
            assertEquals(PinStep.Confirm, vm.state.value.step)
            assertEquals(emptyList(), pins.saved)
            vm.type("4321")
            assertIs<PinEffect.NavigateToAppLock>(awaitItem())
        }
        assertEquals(listOf("4321"), pins.saved)
    }

    @Test
    fun `a mismatch restarts at Enter, stores nothing and shakes`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = null)
        val vm = viewModel(PinMode.Set, pins)
        vm.effects.test {
            vm.type("4321")
            vm.type("1111")
            assertIs<PinEffect.ShakeKeypad>(awaitItem())
        }
        assertEquals(PinStep.Enter, vm.state.value.step)
        assertEquals(PinError.Mismatch, vm.state.value.errorKind)
        assertEquals(emptyList(), pins.saved)
    }

    @Test
    fun `Change proves the old PIN first and leaves by NavigateBack`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = "1234")
        val vm = viewModel(PinMode.Change, pins)
        vm.effects.test {
            vm.type("1234")
            assertEquals(PinStep.Enter, vm.state.value.step)
            vm.type("5678")
            vm.type("5678")
            assertIs<PinEffect.NavigateBack>(awaitItem())
        }
        assertEquals(listOf("1234"), pins.verified)
        assertEquals(listOf("5678"), pins.saved)
    }

    @Test
    fun `a wrong old PIN never reaches the new-PIN step`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = "1234")
        val vm = viewModel(PinMode.Change, pins)
        vm.type("9999")
        assertEquals(PinStep.Verify, vm.state.value.step)
        assertEquals(emptyList(), pins.saved)
    }

    // ── back, and the failure arm ─────────────────────────────────────────────────────────────
    @Test
    fun `back at Confirm returns to Enter instead of leaving`() = main.runVmTest {
        val vm = viewModel(PinMode.Set, FakeAppLockPinRepository(storedPin = null))
        vm.effects.test {
            vm.type("4321")
            vm.onIntent(PinIntent.BackPressed)
            expectNoEvents()
        }
        assertEquals(PinStep.Enter, vm.state.value.step)
        assertEquals(0, vm.state.value.digits)
    }

    @Test
    fun `a repository failure lowers isSubmitting and surfaces a message`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = "1234")
        pins.failure = AppError.Storage(cause = "datastore")
        val vm = viewModel(PinMode.Verify, pins)
        vm.effects.test {
            vm.type("1234")
            assertIs<PinEffect.ShowMessage>(awaitItem())
        }
        assertEquals(false, vm.state.value.isSubmitting)
        assertEquals(true, vm.state.value.acceptsInput)
    }

    @Test
    fun `a rejection carries the lockout deadline the repository decided`() = main.runVmTest {
        val pins = FakeAppLockPinRepository(storedPin = "1234")
        val deadline = Instant.fromEpochMilliseconds(120_000L)
        pins.nextVerdict = PinVerdict.Rejected(PinLockout(failedAttempts = 5, lockedUntil = deadline))
        val vm = viewModel(PinMode.Verify, pins)
        vm.type("0000")
        assertEquals(deadline, vm.state.value.lockedOutUntil)
        assertEquals(5, vm.state.value.failedAttempts)
    }
}
