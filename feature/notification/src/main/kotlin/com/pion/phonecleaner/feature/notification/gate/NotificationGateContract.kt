package com.pion.phonecleaner.feature.notification.gate

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState

/**
 * `gate` — the notification-cleaner entry (`docs/screens/17-notification-and-permissions.md` §1.1).
 *
 * It collapses `ComplectivActivity` + `ScamotrudActivity` + the static router `od.i.H()`. The two
 * competitor Activities are the same composable differing in exactly three values, and neither has a
 * ViewModel, a dialog or an observable; `od.i.H()` is a three-way `if` reading two preference keys
 * synchronously from whatever thread calls it, including `Application` (§1.5).
 */
enum class NotificationGateReason {
    /**
     * A real third value, not a spinner: the screen paints only the toolbar for the frame it takes to
     * read the settings flow, and the CTA must never be tappable before the reason is known (§1.1).
     */
    Resolving,
    ListenerAccessMissing,
    HidingDisabled,
}

data class NotificationGateState(
    val reason: NotificationGateReason = NotificationGateReason.Resolving,
    /** Set when we hand the user to the system listener-access screen; cleared on the next start. */
    val isAwaitingSystemGrant: Boolean = false,
    val error: AppError? = null,
) : UiState {
    val isReady: Boolean get() = reason != NotificationGateReason.Resolving
}

sealed interface NotificationGateIntent : UiIntent {
    /**
     * Reported by the composable on every `ON_START`. It is the only way to learn that a special-access
     * grant changed: `BIND_NOTIFICATION_LISTENER_SERVICE` has no callback and no permission result, and
     * the competitor's XXPermissions callback ignores `allGranted`, so a decline is routed as an
     * accept (§1.5).
     *
     * DEVIATION, deliberate — §1.1 writes this as `AccessResolved(hasListenerAccess: Boolean)`, with the
     * composable reading the platform. §4.3 of the same appendix states the opposing rule for the
     * identical situation on `permissionmanager`: *"`ResumeReporter` supplies only the timing. The
     * platform reads themselves live in `PermissionRepository`."* Reading listener access in a
     * composable would put a second copy of `AndroidPermissionRepository.listenerEnabled()` in the app,
     * and that predicate is exactly where the competitor's hand-parsed
     * `enabled_notification_listeners` matches a package whose name is a prefix of ours. Timing from
     * the composable, the read from the port.
     */
    data object ScreenStarted : NotificationGateIntent

    data object PrimaryCtaTapped : NotificationGateIntent

    data object BackPressed : NotificationGateIntent
}

sealed interface NotificationGateEffect : UiEffect {
    data object OpenNotificationListenerSettings : NotificationGateEffect

    /** `popUpTo(gate) { inclusive = true }` — the gate is not a screen to come back to. */
    data object NavigateToHiddenList : NotificationGateEffect

    data object NavigateBack : NotificationGateEffect

    data class ShowMessage(val error: AppError) : NotificationGateEffect
}
