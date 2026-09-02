package com.pion.phonecleaner.feature.notification.gate

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.ObserveNotificationHidingSettingsUseCase
import com.pion.phonecleaner.domain.usecase.SetNotificationHidingUseCase

/**
 * The three-way branch `od.i.H()` performs inside a persistence singleton, reduced by a ViewModel
 * (`docs/screens/17-notification-and-permissions.md` §1.2).
 *
 * In MVI navigation is an Effect, so the branch cannot live in a router — and this gate is the only
 * entry point the rest of the app knows about the notification cleaner.
 *
 * **No dispatcher is named.** `PermissionRepository` answers on the caller's thread by design (one
 * cheap `system_server` round trip), and the settings flow does its join on `dispatchers.default`
 * inside the store.
 *
 * **No `Job` field.** One `init` collector, a structural child of `viewModelScope`.
 */
class NotificationGateViewModel(
    private val observeSettings: ObserveNotificationHidingSettingsUseCase,
    private val setHiding: SetNotificationHidingUseCase,
    private val permissions: PermissionRepository,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<NotificationGateState, NotificationGateIntent, NotificationGateEffect>(
    NotificationGateState(),
    log,
) {

    /**
     * The master switch, kept current. It is a nullable field rather than a flag on `State` because
     * "not read yet" is not something the screen renders — it is what [NotificationGateReason.Resolving]
     * already says.
     */
    private var isMasterEnabled: Boolean? = null

    /** Whether the composable has reported an `ON_START` yet. Same reason. */
    private var hasStarted: Boolean = false

    init {
        observeSettings().collectSafely(
            onError = { error -> setState { copy(error = error) } },
            onEach = ::onSettings,
        )
    }

    override fun onIntent(intent: NotificationGateIntent) {
        when (intent) {
            NotificationGateIntent.ScreenStarted -> onScreenStarted()
            NotificationGateIntent.PrimaryCtaTapped -> onPrimaryCta()
            NotificationGateIntent.BackPressed -> sendEffect(NotificationGateEffect.NavigateBack)
        }
    }

    private fun onSettings(settings: NotificationHidingSettings) {
        isMasterEnabled = settings.isMasterEnabled
        resolve()
    }

    private fun onScreenStarted() {
        hasStarted = true
        // Coming back from the system settings screen is the only signal that a listener grant
        // changed, so the awaiting flag is cleared here rather than on a callback that does not exist.
        setState { copy(isAwaitingSystemGrant = false) }
        resolve()
    }

    /**
     * The resolution order of §1.2, and it runs only once **both** inputs are known: no listener
     * access wins over the master switch, because the interceptor cannot run at all without it.
     *
     * `FeatureCatalog.requires(FeatureId.NotificationCleaner)` states the same precondition, which is
     * what keeps the gate and the catalogue from drifting apart.
     */
    private fun resolve() {
        val master = isMasterEnabled
        if (!hasStarted || master == null) return
        when {
            !permissions.isGranted(AppPermission.NotificationListener) ->
                setState { copy(reason = NotificationGateReason.ListenerAccessMissing) }

            !master -> setState { copy(reason = NotificationGateReason.HidingDisabled) }

            else -> {
                setState { copy(reason = NotificationGateReason.Resolving) }
                sendEffect(NotificationGateEffect.NavigateToHiddenList)
            }
        }
    }

    /**
     * `Resolving` is ignored — the CTA is not tappable before the reason is known, and a reducer that
     * cannot be reached from an un-tappable control is still written, because state can outrun a frame.
     *
     * On `HidingDisabled` the CTA **flips the master switch directly** and navigates. The competitor's
     * *"Enable"* enables nothing: it navigates to a settings screen and asks the user to find a switch
     * the app already owns — two taps and a screen change to flip its own boolean (§1.5).
     */
    private fun onPrimaryCta() {
        when (currentState.reason) {
            NotificationGateReason.Resolving -> Unit

            NotificationGateReason.ListenerAccessMissing -> {
                setState { copy(isAwaitingSystemGrant = true) }
                sendEffect(NotificationGateEffect.OpenNotificationListenerSettings)
            }

            NotificationGateReason.HidingDisabled -> enableHiding()
        }
    }

    private fun enableHiding() {
        launchSafely(
            onError = { error ->
                setState { copy(error = error) }
                sendEffect(NotificationGateEffect.ShowMessage(error))
            },
        ) {
            when (val result = setHiding.master(enabled = true)) {
                is AppResult.Success -> sendEffect(NotificationGateEffect.NavigateToHiddenList)
                is AppResult.Failure -> {
                    // The switch renders from the persisted flow, so a failed write leaves the wall up
                    // rather than navigating to a list the store says is paused.
                    setState { copy(error = result.error) }
                    sendEffect(NotificationGateEffect.ShowMessage(result.error))
                }
            }
        }
    }
}
