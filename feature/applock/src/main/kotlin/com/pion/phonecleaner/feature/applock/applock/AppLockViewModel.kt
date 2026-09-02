package com.pion.phonecleaner.feature.applock.applock

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.applock.AppLockSettings
import com.pion.phonecleaner.domain.model.applock.LockableApp
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ObserveAppLockSettingsUseCase
import com.pion.phonecleaner.domain.usecase.ObserveLockableAppsUseCase
import com.pion.phonecleaner.domain.usecase.SetAppLockedUseCase
import com.pion.phonecleaner.domain.usecase.SetLockNewlyInstalledUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The App Lock home (`docs/screens/16-app-lock.md` §1.2).
 *
 * **No `Job` field, and nothing is cancelled by hand.** The two `init` collectors and every
 * in-flight toggle are structural children of `viewModelScope`. `Chaennia` has no `onCleared`, no
 * job field and no cancellation at all (`docs/reverse-engineering/16-app-lock.md` §4.4).
 *
 * **No dispatcher is named here.** The enumeration runs on `dispatchers.default` inside
 * `AppLockRepository` and the DataStore writes on `dispatchers.io` inside
 * `AppLockSettingsRepository` (`LLM.md` §6.5).
 *
 * ANALYTICS — this ViewModel takes no `AnalyticsRepository`. `docs/screens/16-app-lock.md` §1.2 puts
 * one in the constructor but names no event for it, and the one existing arm that would fit,
 * `AnalyticsEvent.FeatureOpened`, is already fired by `HomeViewModel`'s `FeatureTapped` arm
 * (`HomeViewModel.kt:139`) — firing it again here would double-count every entry from home and
 * single-count every other entry. The arms this cluster would need are reported to the owner rather
 * than invented, because an arm needs a verified id in
 * `domain/repository/AnalyticsRepository.kt` **and** two branches in
 * `data/analytics/RemoteAnalyticsRepository.kt`, neither of which this cluster owns.
 */
class AppLockViewModel(
    private val observeLockableApps: ObserveLockableAppsUseCase,
    private val setAppLocked: SetAppLockedUseCase,
    private val observeAppLockSettings: ObserveAppLockSettingsUseCase,
    private val setLockNewlyInstalled: SetLockNewlyInstalledUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<AppLockState, AppLockIntent, AppLockEffect>(AppLockState(), log) {

    init {
        // Exactly two collectors, both `collectSafely`. `onError` on each lowers `isScanning` —
        // MVI §1: onError must lower every flag the call raised, or the screen is stuck showing an
        // animation over a list it will never render.
        observeLockableApps().collectSafely(
            onError = { setState { copy(isScanning = false, error = it) } },
            onEach = ::onApps,
        )
        observeAppLockSettings().collectSafely(
            onError = { setState { copy(isScanning = false, error = it) } },
            onEach = ::onSettings,
        )
        // Fire and forget: bookkeeping the user did not ask for, so a failure is logged and never
        // surfaced. It also FIXES the competitor, which stamps the Permission Manager's key from
        // this screen — two features sharing one usage timestamp (§1.5).
        launchSafely { markFeatureUsed(FeatureId.AppLock) }
    }

    override fun onIntent(intent: AppLockIntent) {
        when (intent) {
            AppLockIntent.ScanAnimationFinished -> setState { copy(isScanning = false) }
            is AppLockIntent.TabSelected -> setState { copy(selectedTab = intent.tab) }
            is AppLockIntent.AppRowTapped -> onRowTapped(intent.packageName)
            AppLockIntent.UnlockConfirmed -> onUnlockConfirmed()
            AppLockIntent.UnlockDismissed -> setState { copy(pendingUnlockPackage = null) }
            AppLockIntent.PermissionSheetDismissed ->
                setState { copy(isPermissionSheetVisible = false) }
            AppLockIntent.GrantOverlayTapped ->
                sendEffect(AppLockEffect.RequestOverlayPermission)
            AppLockIntent.GrantUsageStatsTapped ->
                sendEffect(AppLockEffect.RequestUsageStatsPermission)
            is AppLockIntent.LockNewlyInstalledChanged -> onLockNewlyInstalled(intent.enabled)
            is AppLockIntent.PermissionsResolved -> onPermissions(intent)
            AppLockIntent.SettingsTapped -> sendEffect(AppLockEffect.NavigateToSettings)
            AppLockIntent.BackPressed -> sendEffect(AppLockEffect.NavigateBack)
        }
    }

    private fun onApps(apps: ImmutableList<LockableApp>) {
        setState { copy(apps = apps, error = null) }
    }

    private fun onSettings(settings: AppLockSettings) {
        setState { copy(lockNewlyInstalled = settings.lockNewlyInstalled) }
    }

    /**
     * The one place a row tap is interpreted. Three outcomes, decided here and not in a router:
     * `md.g1.g0` holds 14 gate lambdas inside the competitor's router, so a destination and its
     * precondition are edited in different files (`LLM.md` §7.1).
     */
    private fun onRowTapped(packageName: String) {
        val state = currentState
        if (!state.hasAllPermissions) {
            setState { copy(isPermissionSheetVisible = true) }
            return
        }
        val app = state.apps.firstOrNull { it.packageName == packageName } ?: return
        if (app.isLocked) {
            // Unlocking asks first. Locking does not — the risk is asymmetric.
            setState { copy(pendingUnlockPackage = packageName) }
        } else {
            toggle(packageName, locked = true)
        }
    }

    private fun onUnlockConfirmed() {
        val packageName = currentState.pendingUnlockPackage ?: return
        setState { copy(pendingUnlockPackage = null) }
        toggle(packageName, locked = false)
    }

    /**
     * The per-row guard. The package leaves [AppLockState.togglingPackages] on **both** `AppResult`
     * arms and in `onError` — MVI §1, "`onError` must lower every flag the call raised".
     */
    private fun toggle(packageName: String, locked: Boolean) {
        if (packageName in currentState.togglingPackages) return
        setState { copy(togglingPackages = (togglingPackages + packageName).toImmutableSet()) }
        launchSafely(
            onError = { error ->
                setState { copy(togglingPackages = clearToggle(packageName), error = error) }
                sendEffect(AppLockEffect.ShowMessage(error))
            },
        ) {
            when (val result = setAppLocked(packageName, locked)) {
                is AppResult.Success ->
                    setState { copy(togglingPackages = clearToggle(packageName)) }
                is AppResult.Failure -> {
                    setState {
                        copy(togglingPackages = clearToggle(packageName), error = result.error)
                    }
                    sendEffect(AppLockEffect.ShowMessage(result.error))
                }
            }
        }
    }

    private fun onLockNewlyInstalled(enabled: Boolean) {
        launchSafely(
            onError = { error ->
                setState { copy(error = error) }
                sendEffect(AppLockEffect.ShowMessage(error))
            },
        ) {
            val result = setLockNewlyInstalled(enabled)
            // The switch renders from the persisted flow, so a failed write leaves it where it was
            // rather than showing a state nothing stored.
            if (result is AppResult.Failure) sendEffect(AppLockEffect.ShowMessage(result.error))
        }
    }

    /** The single write path for the two permission booleans. */
    private fun onPermissions(intent: AppLockIntent.PermissionsResolved) {
        setState {
            copy(
                hasOverlayPermission = intent.overlay,
                hasUsageStatsPermission = intent.usageStats,
                // A sheet asking for a grant the user has just given must close itself.
                isPermissionSheetVisible =
                    isPermissionSheetVisible && !(intent.overlay && intent.usageStats),
            )
        }
    }
}

private fun AppLockState.clearToggle(packageName: String) =
    (togglingPackages - packageName).toImmutableSet()
