package com.pion.phonecleaner.feature.applock.pin

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.applock.PIN_LENGTH
import com.pion.phonecleaner.domain.model.applock.PinLockout
import com.pion.phonecleaner.domain.model.applock.PinMode
import com.pion.phonecleaner.domain.model.applock.PinVerdict
import com.pion.phonecleaner.domain.usecase.SavePinUseCase
import com.pion.phonecleaner.domain.usecase.VerifyPinUseCase

/**
 * Set, change and verify — one route, three modes (`docs/screens/16-app-lock.md` §2.2).
 *
 * ### The digits
 *
 * `entered` is a `private val` and the **only** place a digit lives: it never reaches `PinState`,
 * never reaches a composable, and is cleared on every submit and in `onCleared`. The competitor
 * keeps the PIN as a `List<Integer>` on a `View` **and** as a `String` field on the Activity for the
 * lifetime of the screen — both of them in any heap dump. `firstEntry` holds the first of two
 * entries for exactly as long as it takes to compare the second against it.
 *
 * ### What this class does not do
 *
 * * **No KDF, no comparison, no lockout arithmetic** — all three are below `AppLockPinRepository`,
 *   on `dispatchers.default` inside the implementation. This class names no dispatcher.
 * * **No countdown.** `lockedOutUntil` is an absolute instant and `PinIntent.LockoutElapsed` is
 *   raised by the notice that renders it; a ticker here would make the deadline depend on something
 *   staying alive.
 * * **No copy** — the prompt is chosen in the composable from `step` and `errorKind` (MVI §5).
 *
 * ANALYTICS — §2.2 puts an `AnalyticsRepository` in the constructor and names no event for it. No
 * arm in `domain/repository/AnalyticsRepository.kt` fits, and an arm needs a verified id there plus
 * two branches in `data/analytics/RemoteAnalyticsRepository.kt`; both files belong to other owners,
 * so the arms are reported rather than invented.
 */
class PinViewModel(
    savedStateHandle: SavedStateHandle,
    private val verifyPin: VerifyPinUseCase,
    private val savePin: SavePinUseCase,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<PinState, PinIntent, PinEffect>(savedStateHandle.initialPinState(), log) {

    private val entered = mutableListOf<Int>()
    private var firstEntry: String? = null

    override fun onIntent(intent: PinIntent) {
        when (intent) {
            is PinIntent.DigitPressed -> onDigit(intent.digit)
            PinIntent.BackspacePressed -> onBackspace()
            PinIntent.BackPressed -> onBack()
            // The repository stays the authority: an attempt that is somehow still early comes
            // back as LockedOut again rather than being compared.
            PinIntent.LockoutElapsed -> setState { copy(lockedOutUntil = null) }
        }
    }

    override fun onCleared() {
        clearBuffers()
        super.onCleared()
    }

    private fun onDigit(digit: Int) {
        if (!currentState.acceptsInput || entered.size >= PIN_LENGTH) return
        entered += digit
        setState { copy(digits = entered.size, errorKind = null) }
        if (entered.size == PIN_LENGTH) submit()
    }

    private fun onBackspace() {
        if (!currentState.acceptsInput || entered.isEmpty()) return
        entered.removeAt(entered.lastIndex)
        setState { copy(digits = entered.size, errorKind = null) }
    }

    /** At `Confirm`, back returns to `Enter` — parity with the competitor — otherwise it leaves. */
    private fun onBack() {
        if (currentState.step == PinStep.Confirm) {
            clearBuffers()
            setState { copy(step = PinStep.Enter, digits = 0, errorKind = null) }
        } else {
            sendEffect(PinEffect.NavigateBack)
        }
    }

    /**
     * The buffer is drained **before** the coroutine starts, so nothing is holding four digits while
     * a KDF runs. `isSubmitting` closes the pad so a fifth tap cannot race the fourth.
     */
    private fun submit() {
        val candidate = entered.joinToString(separator = "")
        entered.clear()
        setState { copy(digits = 0, isSubmitting = true) }
        launchSafely(
            onError = { error ->
                setState { copy(isSubmitting = false) }
                sendEffect(PinEffect.ShowMessage(error))
            },
        ) {
            when {
                currentState.step == PinStep.Verify -> gateOldPin(candidate)
                currentState.mode == PinMode.Verify -> unlock(candidate)
                currentState.step == PinStep.Enter -> stashFirstEntry(candidate)
                else -> confirmSecondEntry(candidate)
            }
        }
    }

    /** `PinMode.Change`'s gate: `SacskipActivity.java:99-105` never asks for the old PIN at all. */
    private suspend fun gateOldPin(candidate: String) =
        onVerdict(candidate) { setState { copy(step = PinStep.Enter, errorKind = null) } }

    private suspend fun unlock(candidate: String) =
        onVerdict(candidate) { sendEffect(PinEffect.NavigateToAppLock) }

    /**
     * The one place a [PinVerdict] is interpreted, shared by the `Change` gate and `Verify` mode;
     * [onVerified] is the only difference. `isSubmitting` is lowered on every arm — MVI §1,
     * "`onError` must lower every flag the call raised".
     */
    private suspend fun onVerdict(candidate: String, onVerified: () -> Unit) {
        when (val result = verifyPin(candidate)) {
            is AppResult.Failure -> {
                setState { copy(isSubmitting = false) }
                sendEffect(PinEffect.ShowMessage(result.error))
            }

            is AppResult.Success -> when (val verdict = result.value) {
                PinVerdict.Verified -> {
                    setState { copy(isSubmitting = false, errorKind = null, failedAttempts = 0, lockedOutUntil = null) }
                    onVerified()
                }

                is PinVerdict.Rejected -> onRejected(verdict.lockout)
                is PinVerdict.LockedOut ->
                    setState { copy(isSubmitting = false, lockedOutUntil = verdict.until) }

                // Not a failure: `od.o0.j()` takes this branch when nothing is stored, and the
                // answer is to set a PIN rather than to show an error. The route argument cannot be
                // rewritten from here, so the flow becomes a `Set` in place.
                PinVerdict.NotSet -> {
                    clearBuffers()
                    setState {
                        copy(mode = PinMode.Set, step = PinStep.Enter, digits = 0, isSubmitting = false, errorKind = null)
                    }
                }
            }
        }
    }

    private fun onRejected(lockout: PinLockout) {
        setState {
            copy(
                isSubmitting = false,
                errorKind = PinError.WrongPin,
                failedAttempts = lockout.failedAttempts,
                lockedOutUntil = lockout.lockedUntil,
            )
        }
        sendEffect(PinEffect.ShakeKeypad)
    }

    private fun stashFirstEntry(candidate: String) {
        firstEntry = candidate
        setState { copy(step = PinStep.Confirm, isSubmitting = false, errorKind = null) }
    }

    private suspend fun confirmSecondEntry(candidate: String) {
        val first = firstEntry
        firstEntry = null
        if (first == null || first != candidate) {
            setState { copy(step = PinStep.Enter, errorKind = PinError.Mismatch, isSubmitting = false) }
            sendEffect(PinEffect.ShakeKeypad)
            return
        }
        when (val result = savePin(candidate)) {
            is AppResult.Failure -> {
                setState { copy(isSubmitting = false) }
                sendEffect(PinEffect.ShowMessage(result.error))
            }
            // Awaited before the navigation: the write is durable by the time the next screen opens.
            is AppResult.Success -> {
                setState { copy(isSubmitting = false) }
                sendEffect(
                    if (currentState.mode == PinMode.Change) PinEffect.NavigateBack
                    else PinEffect.NavigateToAppLock,
                )
            }
        }
    }

    private fun clearBuffers() {
        entered.clear()
        firstEntry = null
    }

}
