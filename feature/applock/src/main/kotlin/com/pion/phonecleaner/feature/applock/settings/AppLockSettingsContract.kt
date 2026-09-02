package com.pion.phonecleaner.feature.applock.settings

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState

/**
 * Which dialog this screen is showing, held as a **nullable field on `State`**.
 *
 * `docs/screens/16-app-lock.md` §4.1 declares the confirmation as an Effect,
 * `ShowConfirm(spec: ConfirmSpec)`. It is not one here, and the reason is written into `ConfirmSpec`
 * itself (`:core:ui/component/dialog/ConfirmSpec.kt`) and into `LLM.md` §7.4: *"A dialog is a
 * nullable field on `State`, never an `Effect`"* — an Effect is a one-shot instruction the UI
 * performs and forgets, and a dialog is a visible condition that must survive a rotation. All
 * eighteen competitor dialogs vanish on rotation, structurally, for exactly this reason.
 *
 * `ConfirmSpec` itself is also not used: it carries a `@PluralsRes` body plus a `count`, and the
 * count would be the number of locked apps — a value this screen's one upstream
 * (`observeAppLockSettings()`, `docs/screens/16-app-lock.md` §4.2) does not carry. A fabricated
 * count is worse than a plain body, so the dialog renders a plain body.
 */
enum class AppLockSettingsDialog {
    /** "This removes the PIN **and** empties the locked-app list." Both, and it says so first. */
    ClearAppLock,
}

/**
 * App Lock settings (`docs/screens/16-app-lock.md` §4.1), replacing `MajimatActivity`.
 *
 * Both flags default to `true` for parity with the competitor's `od.d0` defaults — but they arrive
 * on **one** upstream, so the two switches can never be a frame out of step, and they are DataStore
 * reads off the main thread rather than `getBoolean` on the calling one.
 */
data class AppLockSettingsState(
    val isAppLockEnabled: Boolean = true,
    val lockNewlyInstalled: Boolean = true,
    val dialog: AppLockSettingsDialog? = null,
    /** A write is in flight. The destructive row is disabled while one is. */
    val isClearing: Boolean = false,
    val error: AppError? = null,
) : UiState

sealed interface AppLockSettingsIntent : UiIntent {
    data class AppLockEnabledChanged(val enabled: Boolean) : AppLockSettingsIntent
    data class LockNewlyInstalledChanged(val enabled: Boolean) : AppLockSettingsIntent
    data object ChangePasswordTapped : AppLockSettingsIntent
    data object ClearAppLockTapped : AppLockSettingsIntent
    data object ClearAppLockConfirmed : AppLockSettingsIntent

    /**
     * Not in §4.1's list, and unavoidable once the dialog is state rather than an Effect: something
     * has to lower the field the scrim tap and the back press raise.
     */
    data object ClearAppLockDismissed : AppLockSettingsIntent
    data object BackPressed : AppLockSettingsIntent
}

sealed interface AppLockSettingsEffect : UiEffect {
    /** `PinMode.Change`. The route argument is the `:app` route's, reported not written. */
    data object NavigateToChangePin : AppLockSettingsEffect
    data object NavigateBack : AppLockSettingsEffect

    /** Carries the error: reading `state.error` in the collector reads the pre-failure value. */
    data class ShowMessage(val error: AppError) : AppLockSettingsEffect
}
