package com.pion.phonecleaner.feature.applock.lockscreen

import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.feature.applock.pin.PinError
import kotlin.time.Instant

/**
 * The lock surface drawn over another app (`docs/screens/16-app-lock.md` §3.1), replacing
 * `GratinActivity`.
 *
 * It is kept separate from `pin` because its **exits** differ — it must not go "back" into the app
 * it is guarding — and because it carries a target package. What it shares with `pin` is the
 * persisted allowance: both go through one `AppLockPinRepository`, so failing four times here and
 * then opening the in-app screen does not hand out a fresh one (§2.5).
 *
 * [PinError] is imported from the `pin` package rather than redeclared: two enums with the same two
 * constants are two things to keep in step, and the second one is the one that misses the next
 * change. Only `WrongPin` is reachable here — there is no second entry to mismatch against.
 *
 * > **UNKNOWN — biometrics.** §3.1 declares `biometricAvailable`, `BiometricRequested`,
 * > `BiometricResult` and `ShowBiometricPrompt`, and §2.5 proposes `BiometricPrompt` as the primary
 * > unlock. **No biometric library exists in this project**: `biometric` appears nowhere in
 * > `gradle/libs.versions.toml` and in no `build.gradle.kts` (looked for `androidx.biometric` in
 * > both). Those four members are deliberately **not** declared here, because an Effect nothing can
 * > collect is a feature that does not happen (MVI §4) and a state flag nothing can raise is a
 * > fabricated capability. Adding the dependency adds them back, in one change, to this file.
 */
data class LockScreenState(
    /** The app being guarded. Empty only when the extra is missing; see `LockScreenArgs`. */
    val targetPackage: String = "",
    /**
     * Resolved from `InstalledAppsRepository`. The competitor never sets its title, so the user is
     * asked for a PIN by a screen that names nothing (§3.5).
     */
    val targetLabel: String = "",
    /** `0..PIN_LENGTH`. The digits themselves are not here — they are not on any state object. */
    val digits: Int = 0,
    val errorKind: PinError? = null,
    val isSubmitting: Boolean = false,
    val failedAttempts: Int = 0,
    val lockedOutUntil: Instant? = null,
) : UiState {

    val isLockedOut: Boolean get() = lockedOutUntil != null

    /**
     * §3.1 writes `!isLockedOut`. `isSubmitting` is included for the same reason `pin` includes it:
     * the comparison is a KDF with a deliberately high iteration count, so a fifth tap can otherwise
     * race the fourth and start a second one.
     */
    val acceptsInput: Boolean get() = !isSubmitting && !isLockedOut
}

sealed interface LockScreenIntent : UiIntent {
    data class DigitPressed(val digit: Int) : LockScreenIntent
    data object BackspacePressed : LockScreenIntent

    /** Back goes to the launcher — the competitor's behaviour, and deliberately kept. */
    data object BackPressed : LockScreenIntent

    /** Raised by the lockout notice when its countdown reaches zero. */
    data object LockoutElapsed : LockScreenIntent
}

sealed interface LockScreenEffect : UiEffect {
    /** Correct PIN: dismiss and hand the foreground back to `targetPackage`. */
    data object Unlock : LockScreenEffect

    /** Back: dismiss and go to the launcher. An Effect, never a flag in state. */
    data object GoHome : LockScreenEffect

    /** One-shot. A failure animation is not state — replaying it on rotation would be wrong. */
    data object ShakeKeypad : LockScreenEffect
}
