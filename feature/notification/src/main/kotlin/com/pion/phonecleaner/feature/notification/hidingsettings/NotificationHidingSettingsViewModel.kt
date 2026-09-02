package com.pion.phonecleaner.feature.notification.hidingsettings

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import com.pion.phonecleaner.domain.usecase.ObserveNotificationHidingSettingsUseCase
import com.pion.phonecleaner.domain.usecase.SetNotificationHidingUseCase
import kotlinx.collections.immutable.toImmutableSet

/**
 * `docs/screens/17-notification-and-permissions.md` §2.2.
 *
 * **One `init` collector**, over a single upstream carrying both halves, so the master switch and the
 * rows can never be a frame out of step. The competitor binds its list with `addAll` rather than
 * replace, which is correct only because its ViewModel is asked to load exactly once; a flow-driven
 * design makes a second emission normal, and `addAll` would duplicate every row (§2.5).
 *
 * **No dispatcher is named.** The enumeration runs on `dispatchers.default` inside
 * `InstalledAppsRepository` and the writes on `dispatchers.io` inside the store. Every competitor write
 * is a `commit()` on the main thread — one synchronous fsync per switch tap.
 *
 * **No `Job` field.** The collector and every in-flight toggle are structural children of
 * `viewModelScope`.
 */
class NotificationHidingSettingsViewModel(
    private val observeSettings: ObserveNotificationHidingSettingsUseCase,
    private val setHiding: SetNotificationHidingUseCase,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<
    NotificationHidingSettingsState,
    NotificationHidingSettingsIntent,
    NotificationHidingSettingsEffect,
    >(NotificationHidingSettingsState(), log) {

    init {
        observe()
    }

    override fun onIntent(intent: NotificationHidingSettingsIntent) {
        when (intent) {
            is NotificationHidingSettingsIntent.MasterToggled -> onMasterToggled(intent.enabled)
            is NotificationHidingSettingsIntent.AppToggled -> onAppToggled(intent)
            NotificationHidingSettingsIntent.RetryTapped -> onRetry()
            NotificationHidingSettingsIntent.BackPressed ->
                sendEffect(NotificationHidingSettingsEffect.NavigateBack)
        }
    }

    private fun observe() {
        observeSettings().collectSafely(
            // MVI §1: onError lowers every flag the call raised, or the screen shows skeletons over a
            // list that will never arrive.
            onError = { error -> setState { copy(isLoading = false, error = error) } },
            onEach = ::onSettings,
        )
    }

    private fun onSettings(settings: NotificationHidingSettings) {
        setState {
            copy(
                isLoading = false,
                isMasterEnabled = settings.isMasterEnabled,
                apps = settings.apps,
                error = null,
            )
        }
    }

    /**
     * **It never touches `apps`.** The master switch is written only by the master switch: turning off
     * the last enabled app must not silently write it, which is what the competitor does — a per-app
     * action mutating a global setting with no undo and no message, after which its list disappears
     * entirely (§2.5). At `enabledCount == 0` the card shows an inline hint and writes nothing.
     */
    private fun onMasterToggled(enabled: Boolean) {
        launchSafely(onError = ::report) {
            val result = setHiding.master(enabled)
            // The switch renders from the persisted flow, so a failed write leaves it where it was
            // rather than showing a state nothing stored.
            if (result is AppResult.Failure) report(result.error)
        }
    }

    /**
     * The per-row guard. The package leaves [NotificationHidingSettingsState.togglingPackages] on
     * **both** `AppResult` arms and in `onError`.
     */
    private fun onAppToggled(intent: NotificationHidingSettingsIntent.AppToggled) {
        val packageName = intent.packageName
        if (packageName in currentState.togglingPackages) return
        setState { copy(togglingPackages = (togglingPackages + packageName).toImmutableSet()) }
        launchSafely(
            onError = { error ->
                clearToggle(packageName)
                report(error)
            },
        ) {
            val result = setHiding(packageName, intent.enabled)
            clearToggle(packageName)
            if (result is AppResult.Failure) report(result.error)
        }
    }

    /**
     * Re-opening the collector is correct **only** because the previous one is already finished:
     * `collectSafely` ends its job when the upstream throws, which is the one way this screen reaches
     * an error at all. The guard on [NotificationHidingSettingsState.error] is what keeps a retry with
     * nothing to retry from leaving two collectors on one flow — the shape a `Job` field would
     * otherwise be introduced to manage.
     */
    private fun onRetry() {
        if (currentState.error == null) return
        setState { copy(isLoading = true, error = null) }
        observe()
    }

    private fun clearToggle(packageName: String) {
        setState { copy(togglingPackages = (togglingPackages - packageName).toImmutableSet()) }
    }

    private fun report(error: AppError) {
        setState { copy(error = error) }
        sendEffect(NotificationHidingSettingsEffect.ShowMessage(error))
    }
}
