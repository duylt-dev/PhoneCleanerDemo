package com.pion.phonecleaner.feature.applock.pin

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.applock.PinMode
import kotlin.time.Instant

/**
 * Where in the flow the pad is.
 *
 * `docs/screens/16-app-lock.md` §2.1 writes two constants, `Enter` and `Confirm`. [Verify] is the
 * third, and it is required by §2.5 of the same appendix: *"`PinMode.Change` runs `Verify` first, in
 * the same flow, before it accepts a new PIN"*. Without it that rule has nowhere to live, and the
 * competitor's actual defect stands — `SacskipActivity.java:99-105` asks for a new PIN twice and
 * never for the old one, so anyone already past the entry gate can silently re-key.
 */
enum class PinStep {
    /** `PinMode.Change` only: prove the old PIN before a new one is accepted. */
    Verify,
    Enter,
    Confirm,
}

/** Why the pad is showing an error. `LockedOut` is not here: it is `lockedOutUntil` on the state. */
enum class PinError { WrongPin, Mismatch }

/**
 * Set, change and verify — one route, one contract, three modes
 * (`docs/screens/16-app-lock.md` §2.1). The competitor already carried `mode` as an `int` extra.
 *
 * > **The entered digits are not on this object.** Only [digits], the count, which is all four dots
 * > need. The buffer is a `private val` inside `PinViewModel`, cleared on every submit and in
 * > `onCleared`. This is the single most important divergence from the competitor, where the PIN
 * > lives as a `List<Integer>` on a `View` **and** as a `String` field on the Activity for the
 * > lifetime of the screen — both of them in any heap dump.
 */
data class PinState(
    val mode: PinMode = PinMode.Verify,
    val step: PinStep = PinStep.Enter,
    /** `0..PIN_LENGTH`. The digits themselves are not here. */
    val digits: Int = 0,
    val errorKind: PinError? = null,
    val isSubmitting: Boolean = false,
    val failedAttempts: Int = 0,
    val lockedOutUntil: Instant? = null,
) : UiState {

    /**
     * Both `Set` and `Change` are two-step, so both show it. The competitor shows the indicator in
     * Change mode only (`SacskipActivity.R()`), although Set is equally two-step.
     */
    val showsStepIndicator: Boolean get() = mode != PinMode.Verify && step != PinStep.Verify

    val isLockedOut: Boolean get() = lockedOutUntil != null

    /** `false` while a comparison is in flight, so a fifth tap cannot race the fourth. */
    val acceptsInput: Boolean get() = !isSubmitting && !isLockedOut
}

sealed interface PinIntent : UiIntent {
    data class DigitPressed(val digit: Int) : PinIntent
    data object BackspacePressed : PinIntent
    data object BackPressed : PinIntent

    /** Raised by the lockout notice when its countdown reaches zero. */
    data object LockoutElapsed : PinIntent
}

sealed interface PinEffect : UiEffect {
    /** `Set` and `Verify` succeeded. */
    data object NavigateToAppLock : PinEffect

    /** `Change` succeeded, or a plain back. */
    data object NavigateBack : PinEffect

    /** One-shot. A failure animation is not state — replaying it on rotation would be wrong. */
    data object ShakeKeypad : PinEffect

    data class ShowMessage(val error: AppError) : PinEffect
}
