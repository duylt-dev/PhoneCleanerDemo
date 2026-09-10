package com.pion.phonecleaner.feature.device.runningappsscan

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.device.MemoryInfo

/**
 * `runningappsscan` (`docs/screens/18-device-battery-and-apps.md` §6.1). Replaces `EqualsioActivity`.
 *
 * ### PENDING OWNER DECISION 3 — §0.1 / `docs/system-architecture.md` §10.1 **P1**, UNSETTLED
 *
 * Whether this cluster asks for `PACKAGE_USAGE_STATS` at all is the owner's to settle. This contract
 * is written so that neither outcome touches it: the enumeration is behind
 * `ListStoppableAppsUseCase`, and the usage-access surface is on `runningapps`, where the user can
 * act on it. Under option A only the repository's data source changes; under option B nothing does.
 * `:data` declares the permission for App Manager's *Last used* column; this cluster reads no usage
 * statistic either way while the decision is open.
 */
@Immutable
data class RunningAppsScanState(
    val progress: Int = 0,
    /** The ring. `null` is a shimmer, never a stale frame and never a zero. */
    val memory: MemoryInfo? = null,
    val isFinished: Boolean = false,
    /** Folds the Activity field `scanning`: Back is refused while this is true. See §2.1. */
    val isBackBlocked: Boolean = true,
    val error: AppError? = null,
) : UiState

sealed interface RunningAppsScanIntent : UiIntent {
    data object BackPressed : RunningAppsScanIntent
}

sealed interface RunningAppsScanEffect : UiEffect {
    /**
     * The enumerated list is **not** on this effect and **not** a route argument: it travels in
     * `DeviceScanSessionStore` (§0.2), because a scan result of unbounded size is exactly the case
     * the session-store pattern exists for — and this route pops itself
     * (`popUpTo(self) { inclusive = true }`, §8), which no argument can cross.
     */
    data object NavigateToRunningApps : RunningAppsScanEffect
    data object NavigateBack : RunningAppsScanEffect
    data object ShowScanInProgressMessage : RunningAppsScanEffect
}
