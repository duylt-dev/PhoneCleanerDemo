package com.pion.phonecleaner.feature.device.batteryinfo

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.device.BatterySnapshot

/**
 * `batteryinfo` (`docs/screens/18-device-battery-and-apps.md` §5.1). Replaces `HospgraActivity`.
 *
 * **Derived, not stored.** The competitor writes six pre-formatted strings, a status label, an icon
 * resource, an `h`/`min` pair and a `SpannableStringBuilder` into its binding. None of that is state:
 * [BatterySnapshot] holds numbers and enums, and the composable derives every cell at render time,
 * where the locale is.
 */
@Immutable
data class BatteryInfoState(
    /** null only for the first frame — and not even then when the scan seeded the session store. */
    val snapshot: BatterySnapshot? = null,
    val error: AppError? = null,
) : UiState {
    val isLoading: Boolean get() = snapshot == null && error == null
}

/**
 * **One intent.** Every other interaction on this screen is a *system* event, not a user action, and
 * a system event belongs in the `Flow` the ViewModel already collects.
 *
 * `ScreenResumed` is deliberately absent: `collectAsStateWithLifecycle` stops and restarts the
 * `callbackFlow` at `STARTED`, which is exactly what the competitor's `onResume`/`onPause`
 * register/unregister pair achieves — without two lifecycle overrides and two `try`/`catch` blocks
 * around `unregisterReceiver`.
 */
sealed interface BatteryInfoIntent : UiIntent {
    data object BackPressed : BatteryInfoIntent
}

sealed interface BatteryInfoEffect : UiEffect {
    data object NavigateBack : BatteryInfoEffect
}
