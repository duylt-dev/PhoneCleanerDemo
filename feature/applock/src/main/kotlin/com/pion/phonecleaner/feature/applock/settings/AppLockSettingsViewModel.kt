package com.pion.phonecleaner.feature.applock.settings

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.applock.AppLockSettings
import com.pion.phonecleaner.domain.usecase.ClearAppLockUseCase
import com.pion.phonecleaner.domain.usecase.ObserveAppLockSettingsUseCase
import com.pion.phonecleaner.domain.usecase.SetAppLockEnabledUseCase
import com.pion.phonecleaner.domain.usecase.SetLockNewlyInstalledUseCase

/**
 * App Lock settings (`docs/screens/16-app-lock.md` §4.2).
 *
 * **Starting and stopping the monitor is not this class's job.** It writes the persisted flag and
 * stops; `ForegroundAppMonitor` observes that flag. `MajimatActivity.java:37-44` calls `od.e0.d()` /
 * `g()` straight from a click listener, so the watchdog's lifetime is decided in a view callback and
 * any other writer of the same preference leaves it out of step.
 *
 * **Both switches render from the persisted upstream, never from the tap.** A failed write therefore
 * leaves the switch where it was rather than showing a position nothing stored — the competitor
 * toggles its `ImageView` drawable first and writes second, so a failed `commit()` leaves the two
 * disagreeing until the screen is re-entered.
 *
 * ANALYTICS — no `AnalyticsRepository` here. §4.2 turns the competitor's back-press snapshot
 * (`ev 102517`/`102518`) into a "setting changed" event fired by the repository at the point of
 * change; no arm for it exists in `domain/repository/AnalyticsRepository.kt`, and an arm needs a
 * verified id there **and** two branches in `data/analytics/RemoteAnalyticsRepository.kt` — neither
 * file this cluster's. The arms are reported to the owner rather than invented.
 */
class AppLockSettingsViewModel(
    private val observeAppLockSettings: ObserveAppLockSettingsUseCase,
    private val setAppLockEnabled: SetAppLockEnabledUseCase,
    private val setLockNewlyInstalled: SetLockNewlyInstalledUseCase,
    private val clearAppLock: ClearAppLockUseCase,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<AppLockSettingsState, AppLockSettingsIntent, AppLockSettingsEffect>(
    AppLockSettingsState(),
    log,
) {

    init {
        // Exactly one collector, over one upstream carrying both flags (§4.2).
        observeAppLockSettings().collectSafely(
            onError = { setState { copy(error = it) } },
            onEach = ::onSettings,
        )
    }

    override fun onIntent(intent: AppLockSettingsIntent) {
        when (intent) {
            is AppLockSettingsIntent.AppLockEnabledChanged -> write { setAppLockEnabled(intent.enabled) }
            is AppLockSettingsIntent.LockNewlyInstalledChanged ->
                write { setLockNewlyInstalled(intent.enabled) }

            AppLockSettingsIntent.ChangePasswordTapped ->
                sendEffect(AppLockSettingsEffect.NavigateToChangePin)

            AppLockSettingsIntent.ClearAppLockTapped ->
                setState { copy(dialog = AppLockSettingsDialog.ClearAppLock) }

            AppLockSettingsIntent.ClearAppLockDismissed -> setState { copy(dialog = null) }
            AppLockSettingsIntent.ClearAppLockConfirmed -> onClearConfirmed()
            AppLockSettingsIntent.BackPressed -> sendEffect(AppLockSettingsEffect.NavigateBack)
        }
    }

    private fun onSettings(settings: AppLockSettings) {
        setState {
            copy(
                isAppLockEnabled = settings.isAppLockEnabled,
                lockNewlyInstalled = settings.lockNewlyInstalled,
                error = null,
            )
        }
    }

    /** The one write path for the two switches: a `Failure` is surfaced, never swallowed. */
    private fun write(block: suspend () -> AppResult<Unit>) {
        launchSafely(onError = ::onFailure) {
            val result = block()
            if (result is AppResult.Failure) onFailure(result.error)
        }
    }

    /**
     * The PIN, the salt, the lockout record and the lock list go in one call —
     * `ClearAppLockUseCase` is the transaction, and the dialog said so before it ran (§4.5).
     *
     * `isClearing` is lowered on both `AppResult` arms **and** in `onError`: MVI §1, "`onError` must
     * lower every flag the call raised".
     */
    private fun onClearConfirmed() {
        if (currentState.isClearing) return
        setState { copy(dialog = null, isClearing = true) }
        launchSafely(
            onError = { error ->
                setState { copy(isClearing = false) }
                onFailure(error)
            },
        ) {
            val result = clearAppLock()
            setState { copy(isClearing = false) }
            if (result is AppResult.Failure) onFailure(result.error)
        }
    }

    private fun onFailure(error: AppError) {
        setState { copy(error = error) }
        sendEffect(AppLockSettingsEffect.ShowMessage(error))
    }
}
