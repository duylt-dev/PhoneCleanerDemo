package com.pion.phonecleaner.feature.applock.lockscreen

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.applock.PIN_LENGTH
import com.pion.phonecleaner.domain.model.applock.PinVerdict
import com.pion.phonecleaner.domain.repository.ForegroundAppMonitor
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.usecase.VerifyPinUseCase
import com.pion.phonecleaner.feature.applock.pin.PinError
import kotlin.time.Instant

/**
 * The lock surface's ViewModel (`docs/screens/16-app-lock.md` §3.2).
 *
 * * The buffer is a `private val` here too, cleared on submit and in [onCleared]; no digit reaches
 *   `LockScreenState`, and `PinDots`/`PinKeypad` take a count and emit key events.
 * * Submission goes through the **same** [VerifyPinUseCase] and the same persisted lockout as `pin`.
 * * On success it records the unlock **before** emitting [LockScreenEffect.Unlock] — an explicit
 *   record, replacing the competitor's accidental semantics, where "unlocked" is a side effect of
 *   one static dedup field that nobody can read the rule off (§3.5).
 * * Every coroutine is a structural child of `viewModelScope`; there is no `Job` field.
 *
 * PENDING OWNER DECISION (4) — **the seam is here.** App Lock stops at every process death and after
 * every reboot until the user next opens the app, because there is no `BootCompletedReceiver` and
 * `RECEIVE_BOOT_COMPLETED` is not declared anywhere (`docs/system-architecture.md` §10.1 P6). This
 * class is the *consumer* of `ForegroundAppMonitor.lockRequests`, not its starter: a boot-time start
 * would call `ForegroundAppMonitor.start()` from a receiver in `:data` and nothing in this file
 * would change. Deferred, deliberately, and not decided by this cluster.
 *
 * There is **no message channel**: §3.1 declares no `ShowMessage`, and a surface drawn over another
 * app is the last place to render a repository's error text. A failure is logged by `launchSafely`
 * and shows as a refused attempt.
 */
class LockScreenViewModel(
    savedStateHandle: SavedStateHandle,
    private val verifyPin: VerifyPinUseCase,
    private val installedApps: InstalledAppsRepository,
    private val foregroundAppMonitor: ForegroundAppMonitor,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<LockScreenState, LockScreenIntent, LockScreenEffect>(
    LockScreenState(targetPackage = savedStateHandle.lockTargetPackage()),
    log,
) {

    private val entered = mutableListOf<Int>()

    init {
        resolveLabel()
    }

    override fun onIntent(intent: LockScreenIntent) {
        when (intent) {
            is LockScreenIntent.DigitPressed -> onDigit(intent.digit)
            LockScreenIntent.BackspacePressed -> onBackspace()
            LockScreenIntent.BackPressed -> sendEffect(LockScreenEffect.GoHome)
            LockScreenIntent.LockoutElapsed -> setState { copy(lockedOutUntil = null) }
        }
    }

    override fun onCleared() {
        entered.clear()
        super.onCleared()
    }

    /**
     * The label, and only the label. The icon is loaded by the composable from `targetPackage`; this
     * class never touches a `Drawable`.
     *
     * A failure leaves `targetLabel` empty and the screen prints the package name instead — the
     * prompt still has to appear, because the alternative is an unlocked app.
     */
    private fun resolveLabel() {
        val packageName = currentState.targetPackage
        if (packageName.isEmpty()) return
        launchSafely {
            val result = installedApps.find(packageName)
            val label = (result as? AppResult.Success)?.value?.label ?: return@launchSafely
            setState { copy(targetLabel = label) }
        }
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

    private fun submit() {
        val candidate = entered.joinToString(separator = "")
        entered.clear()
        setState { copy(digits = 0, isSubmitting = true) }
        launchSafely(
            onError = {
                // onError must lower every flag the call raised (MVI §1). A failure is a refused
                // attempt, never an unlock.
                setState { copy(isSubmitting = false, errorKind = PinError.WrongPin) }
                sendEffect(LockScreenEffect.ShakeKeypad)
            },
        ) {
            onVerdict(verifyPin(candidate))
        }
    }

    private fun onVerdict(result: AppResult<PinVerdict>) {
        setState { copy(isSubmitting = false) }
        when (result) {
            is AppResult.Failure -> reject()
            is AppResult.Success -> when (val verdict = result.value) {
                PinVerdict.Verified -> unlock()
                is PinVerdict.Rejected -> reject(
                    attempts = verdict.lockout.failedAttempts,
                    until = verdict.lockout.lockedUntil,
                )

                is PinVerdict.LockedOut -> setState { copy(lockedOutUntil = verdict.until) }
                // Nothing is stored, so nothing can be compared and there is nothing to guard.
                // Refusing forever would strand the user in front of an app they own.
                PinVerdict.NotSet -> unlock()
            }
        }
    }

    /** The record goes in **before** the Effect, so a monitor restart cannot re-lock behind it. */
    private fun unlock() {
        foregroundAppMonitor.noteUnlocked(currentState.targetPackage)
        sendEffect(LockScreenEffect.Unlock)
    }

    private fun reject(
        attempts: Int = currentState.failedAttempts + 1,
        until: Instant? = null,
    ) {
        setState {
            copy(errorKind = PinError.WrongPin, failedAttempts = attempts, lockedOutUntil = until)
        }
        sendEffect(LockScreenEffect.ShakeKeypad)
    }
}
