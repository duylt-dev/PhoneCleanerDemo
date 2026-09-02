package com.pion.phonecleaner.feature.notification.hiddenlist

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.notification.HiddenNotification
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import com.pion.phonecleaner.domain.usecase.ClearHiddenNotificationsUseCase
import com.pion.phonecleaner.domain.usecase.DismissHiddenNotificationUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ObserveHiddenNotificationsUseCase
import com.pion.phonecleaner.domain.usecase.ObserveNotificationHidingSettingsUseCase
import com.pion.phonecleaner.domain.usecase.SetNotificationHidingUseCase
import kotlinx.collections.immutable.ImmutableList

/**
 * `docs/screens/17-notification-and-permissions.md` §3.2.
 *
 * **Two `init` collectors, and that is the whole refresh mechanism.** A Room `Flow` emits what changed
 * and Compose diffs by key; the competitor re-reads the entire list from disk and reloads every icon
 * once per intercepted notification, because its `LiveEventBus` event carries no payload — unbounded
 * work driven by other apps' notification rate (§3.5).
 *
 * **No `Job` field.** The clear is a plain child of `viewModelScope`: leaving the screen cancels it, and
 * because the wipe is committed by then the only thing lost is the animation.
 *
 * **The four-second floor is not here.** It lives inside `ClearHiddenNotificationsUseCase` through the
 * injected `MinimumDuration`, so a test injects `Duration.ZERO` instead of advancing a virtual clock
 * past a `delay` the ViewModel hard-coded.
 */
class HiddenNotificationsViewModel(
    private val observeHidden: ObserveHiddenNotificationsUseCase,
    private val observeSettings: ObserveNotificationHidingSettingsUseCase,
    private val dismiss: DismissHiddenNotificationUseCase,
    private val clearAll: ClearHiddenNotificationsUseCase,
    private val setHiding: SetNotificationHidingUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<HiddenNotificationsState, HiddenNotificationsIntent, HiddenNotificationsEffect>(
    HiddenNotificationsState(),
    log,
) {

    init {
        observeHidden().collectSafely(
            onError = { error -> setState { copy(isLoading = false, error = error) } },
            onEach = ::onNotifications,
        )
        observeSettings().collectSafely(
            onError = { error -> setState { copy(error = error) } },
            onEach = ::onSettings,
        )
        // Fire and forget: bookkeeping the user did not ask for, so a failure is logged, never surfaced.
        launchSafely { markFeatureUsed(FeatureId.NotificationCleaner) }
    }

    override fun onIntent(intent: HiddenNotificationsIntent) {
        when (intent) {
            is HiddenNotificationsIntent.NotificationTapped -> onTapped(intent.key)
            HiddenNotificationsIntent.ClearAllTapped -> onClearAll()
            HiddenNotificationsIntent.ClearAnimationFinished -> onAnimationFinished()
            HiddenNotificationsIntent.ResumeHidingTapped -> onResumeHiding()
            HiddenNotificationsIntent.SettingsTapped ->
                sendEffect(HiddenNotificationsEffect.NavigateToHidingSettings)

            HiddenNotificationsIntent.BackPressed -> onBack()
        }
    }

    private fun onNotifications(notifications: ImmutableList<HiddenNotification>) {
        setState { copy(isLoading = false, notifications = notifications, error = null) }
    }

    private fun onSettings(settings: NotificationHidingSettings) {
        setState { copy(isMasterEnabled = settings.isMasterEnabled) }
    }

    /**
     * Launch first, dismiss second. The competitor drops the row whether or not anything opened, and a
     * missing launch intent fails silently — so "restore" restores nothing and the row vanishes anyway
     * (§3.5). Here the Route reports the outcome by simply not producing an error; the row disappears
     * because the repository `Flow` re-emits, never because this edited a list.
     */
    private fun onTapped(key: String) {
        val target = currentState.notifications.firstOrNull { it.key == key } ?: return
        sendEffect(HiddenNotificationsEffect.LaunchApp(target.packageName))
        launchSafely(onError = ::report) {
            val result = dismiss(key)
            if (result is AppResult.Failure) report(result.error)
        }
    }

    private fun onClearAll() {
        if (currentState.isClearing) return
        setState { copy(clearStage = ClearStage.Running) }
        launchSafely(
            onError = { error ->
                setState { copy(clearStage = ClearStage.Idle) }
                report(error)
            },
        ) {
            when (val result = clearAll()) {
                is AppResult.Success -> setState { copy(clearStage = ClearStage.Finished(result.value)) }
                is AppResult.Failure -> {
                    setState { copy(clearStage = ClearStage.Idle) }
                    report(result.error)
                }
            }
        }
    }

    /** The only thing that ends the clear stage — and the count it carries is what navigates. */
    private fun onAnimationFinished() {
        val stage = currentState.clearStage
        setState { copy(clearStage = ClearStage.Idle) }
        if (stage is ClearStage.Finished) {
            sendEffect(HiddenNotificationsEffect.NavigateToCleanResult(stage.count))
        }
    }

    /**
     * The banner's action writes the master switch **here**, rather than sending the user back to the
     * settings screen to find it. The competitor's equivalent wall navigates and asks the user to flip
     * a boolean the app already owns (§1.5).
     */
    private fun onResumeHiding() {
        launchSafely(onError = ::report) {
            val result = setHiding.master(enabled = true)
            if (result is AppResult.Failure) report(result.error)
        }
    }

    /** A modal block while clearing, without an `onBackPressed` override. */
    private fun onBack() {
        if (currentState.isClearing) {
            sendEffect(HiddenNotificationsEffect.ShowClearInProgressMessage)
        } else {
            sendEffect(HiddenNotificationsEffect.NavigateBack)
        }
    }

    private fun report(error: AppError) {
        setState { copy(error = error) }
        sendEffect(HiddenNotificationsEffect.ShowMessage(error))
    }
}
