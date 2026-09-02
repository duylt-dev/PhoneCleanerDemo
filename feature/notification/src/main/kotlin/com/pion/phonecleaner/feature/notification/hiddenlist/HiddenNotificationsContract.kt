package com.pion.phonecleaner.feature.notification.hiddenlist

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.notification.HiddenNotification
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Where the clear-all run has got to (`docs/screens/17-notification-and-permissions.md` §3.1).
 *
 * The count travels **inside** the stage that owns it rather than as a bare `Int` on a second
 * observable: `clearAllHidingNotifyListObser` and `allHidingNotifyListObser1` are two `LiveData`s that
 * can disagree, and the competitor's `isClearing` is an Activity field that rotation resets while the
 * coroutine is still running — the overlay disappears mid-clear (§3.5).
 */
@Immutable
sealed interface ClearStage {
    data object Idle : ClearStage

    /** The store is being wiped; the animation is playing. */
    data object Running : ClearStage

    /** The wipe is committed. Waiting for the completion animation, then the result screen. */
    data class Finished(val count: Int) : ClearStage
}

data class HiddenNotificationsState(
    val isLoading: Boolean = true,
    val notifications: ImmutableList<HiddenNotification> = persistentListOf(),
    /** Observed, **not navigated on** — the banner is rendered here rather than re-routing (§3.5). */
    val isMasterEnabled: Boolean = true,
    val clearStage: ClearStage = ClearStage.Idle,
    val error: AppError? = null,
) : UiState {
    val isEmpty: Boolean get() = !isLoading && notifications.isEmpty()
    val isClearing: Boolean get() = clearStage != ClearStage.Idle
    val isClearAllEnabled: Boolean get() = notifications.isNotEmpty() && !isClearing
}

sealed interface HiddenNotificationsIntent : UiIntent {
    data class NotificationTapped(val key: String) : HiddenNotificationsIntent

    data object ClearAllTapped : HiddenNotificationsIntent

    /** The completion animation ended. The ONLY thing that ends the clear stage. */
    data object ClearAnimationFinished : HiddenNotificationsIntent

    /** The paused banner's action: it flips the master switch, it does not navigate. */
    data object ResumeHidingTapped : HiddenNotificationsIntent

    data object SettingsTapped : HiddenNotificationsIntent

    data object BackPressed : HiddenNotificationsIntent
}

sealed interface HiddenNotificationsEffect : UiEffect {
    /** The platform call lives in the Route; the ViewModel names no `Intent` and no `Context`. */
    data class LaunchApp(val packageName: String) : HiddenNotificationsEffect

    /**
     * Into the shared `CleanResult` route (`docs/system-architecture.md` §4.6). A **typed count**: the
     * competitor smuggles it through a `fileSize` string extra with an empty unit (§3.5).
     */
    data class NavigateToCleanResult(val clearedCount: Int) : HiddenNotificationsEffect

    data object NavigateToHidingSettings : HiddenNotificationsEffect

    data object NavigateBack : HiddenNotificationsEffect

    data class ShowMessage(val error: AppError) : HiddenNotificationsEffect

    data object ShowClearInProgressMessage : HiddenNotificationsEffect
}
