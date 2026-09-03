package com.pion.phonecleaner.feature.device.runningapps

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.device.MemoryInfo
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.domain.model.device.UsageAccessState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * `runningapps` (`docs/screens/18-device-battery-and-apps.md` §6.2). Replaces `PadndlActivity`.
 *
 * Selection is not a concept here — a row is acted on, not chosen — so there is no id set on state.
 * What *is* on state is the one package we handed to Settings, which the competitor's bare
 * `shouldShowForceStopHint` cannot express: it is an Activity field cleared unconditionally in
 * `onResume`, and it does not even remember which app it was raised for.
 */
@Immutable
data class RunningAppsState(
    val apps: ImmutableList<RunningApp> = persistentListOf(),
    val memory: MemoryInfo? = null,
    val isRefreshing: Boolean = false,
    /**
     * The package we last handed to system Settings. It survives the trip out and back, because the
     * only way this app can learn a force-stop happened is to re-read `FLAG_STOPPED` for *that*
     * package on the way back in (§6.5).
     */
    val awaitingForceStopOf: String? = null,
    /** The instruction sheet is shown **before** we leave, not 500 ms after (§7). */
    val instructionsFor: String? = null,
    /**
     * ### PENDING OWNER DECISION 3 — §0.1 / `docs/system-architecture.md` §10.1 **P1**, UNSETTLED
     *
     * `PACKAGE_USAGE_STATS` is a special access the user must grant by hand in a system Settings
     * screen, and whether this app asks for that friction **is not settled**. This field is the seam:
     * the ungranted path is a rendered rationale with a grant action, so shipping the access and
     * dropping the screen are both one edit away.
     *
     * Two things it does **not** do. It does not gate the list — under today's design the list is
     * the `PackageManager` enumeration and needs nothing — and it does not claim we read usage
     * statistics on this screen, because we do not. The permission is declared for App Manager's
     * *Last used* column, so a grant given from here is real but changes nothing here until option A
     * lands.
     */
    val usageAccess: UsageAccessState = UsageAccessState.Unknown,
    /** "Continue without it". Not persisted: a session-scoped dismissal, so nothing is remembered. */
    val isUsageAccessDismissed: Boolean = false,
    val error: AppError? = null,
) : UiState {

    /**
     * The competitor has **no empty state at all** — a device with no third-party apps gets a
     * header, a ring and an empty box (§6.2).
     */
    val isEmpty: Boolean get() = apps.isEmpty() && !isRefreshing

    /** Rendered only while the decision is open and the user has not waved it away. */
    val isUsageAccessVisible: Boolean
        get() = usageAccess == UsageAccessState.Denied && !isUsageAccessDismissed
}

sealed interface RunningAppsIntent : UiIntent {
    data object ScreenResumed : RunningAppsIntent
    data class StopTapped(val packageName: String) : RunningAppsIntent
    data object InstructionsOpenSettingsTapped : RunningAppsIntent
    data object InstructionsDismissed : RunningAppsIntent
    data object UsageAccessGrantTapped : RunningAppsIntent
    data object UsageAccessDismissed : RunningAppsIntent
    data object SkipTapped : RunningAppsIntent
    data object RetryTapped : RunningAppsIntent
    data object BackPressed : RunningAppsIntent
}

sealed interface RunningAppsEffect : UiEffect {
    /** The Route builds and starts the intent; the ViewModel never touches `android.content`. */
    data class OpenSystemAppInfo(val packageName: String) : RunningAppsEffect

    /** See [RunningAppsState.usageAccess]. Opening this screen needs no manifest declaration. */
    data object OpenUsageAccessSettings : RunningAppsEffect
    data object NavigateBack : RunningAppsEffect

    /** Carries the error. Reading `state.error` in the collector reads the pre-failure value (MVI §4). */
    data class ShowMessage(val error: AppError) : RunningAppsEffect
}
