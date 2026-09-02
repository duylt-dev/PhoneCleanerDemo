package com.pion.phonecleaner.feature.device.devicestatusscan

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState

/**
 * `devicestatusscan` (`docs/screens/18-device-battery-and-apps.md` §2.1). Replaces `AnafalloActivity`.
 *
 * The competitor's `hasShow` field and its `onResume` ad retry have **no analogue here** — they exist
 * only to re-request an interstitial, and there is no ad in this port. That removes an intent, an
 * effect and a lifecycle observer from every scan screen in the cluster.
 */
@Immutable
data class DeviceStatusScanState(
    /** 0..100 — the only thing on screen that moves. */
    val progress: Int = 0,
    val isFinished: Boolean = false,
    /**
     * Folds the Activity field `scanning`: Back is refused while this is true.
     *
     * Deliberately **not** derived from [isFinished]. They agree today and stop agreeing the moment a
     * "cancel scan" affordance is added; the flag is named after what it governs. If that ever needs
     * fixing, the fix is a computed `val` — never two stored fields that can disagree.
     */
    val isBackBlocked: Boolean = true,
    val error: AppError? = null,
) : UiState

sealed interface DeviceStatusScanIntent : UiIntent {
    data object BackPressed : DeviceStatusScanIntent
}

sealed interface DeviceStatusScanEffect : UiEffect {
    /**
     * The readings travel in `DeviceScanSessionStore`, not on this effect: the route pops itself
     * with `popUpTo(self) { inclusive = true }` (§8), and a payload cannot cross that edge as an
     * argument (§0.2, `docs/system-architecture.md` §10.3 **U1**).
     */
    data object NavigateToDetail : DeviceStatusScanEffect
    data object NavigateBack : DeviceStatusScanEffect

    /** A `Snackbar` from the route. The competitor raises a `Toast`, which outlives the screen. */
    data object ShowScanInProgressMessage : DeviceStatusScanEffect
}
