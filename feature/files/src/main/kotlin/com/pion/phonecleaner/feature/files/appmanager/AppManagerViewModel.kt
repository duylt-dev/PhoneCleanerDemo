package com.pion.phonecleaner.feature.files.appmanager

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.app.InstalledAppsProgress
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.LoadInstalledAppsUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.UninstallAppUseCase
import com.pion.phonecleaner.feature.files.component.restoreSelection
import com.pion.phonecleaner.feature.files.component.storeSelection
import kotlinx.coroutines.Job

/**
 * `appmanager` (`docs/screens/14-file-tools-and-app-manager.md` §5.2).
 *
 * **The screen opens regardless of `PACKAGE_USAGE_STATS`.** Denied means one column reads "no usage
 * data" and one sort chip is disabled — sizes and uninstall need no special access (§5.5). This
 * decides nothing about the *running apps* screen, whose grant is still an open owner decision.
 *
 * **The list renders after `Enumerated`; sizes stream in.** The competitor blocks the whole screen
 * behind `awaitAll()` and then pads to 4 000 ms; on a 180-app device the size fan-out is the slow
 * part and is not needed to draw the first screen.
 *
 * **No dispatcher is named here.** `getInstalledApplications` and the `StorageStatsManager` fan-out
 * choose theirs inside their repositories, which take `DispatcherProvider`.
 */
class AppManagerViewModel(
    private val savedState: SavedStateHandle,
    private val loadInstalledApps: LoadInstalledAppsUseCase,
    private val uninstallApp: UninstallAppUseCase,
    private val permissions: PermissionRepository,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<AppManagerState, AppManagerIntent, AppManagerEffect>(AppManagerState(), log) {

    /** ONE scan job, cancel-and-replace. The sizing fan-out is launched inside it, never a field. */
    private var scanJob: Job? = null

    override fun onIntent(intent: AppManagerIntent) {
        when (intent) {
            AppManagerIntent.ScreenStarted -> onScreenStarted()
            AppManagerIntent.GrantUsageAccessPressed -> setState { copy(rationale = true) }
            AppManagerIntent.RationaleDismissed -> setState { copy(rationale = false) }
            AppManagerIntent.RationaleContinued -> {
                setState { copy(rationale = false) }
                sendEffect(AppManagerEffect.OpenUsageAccessSettings)
            }

            is AppManagerIntent.RowToggled -> saveSelection { withToggled(intent.packageName) }
            AppManagerIntent.SelectAllToggled -> saveSelection { withSelectAllToggled() }
            is AppManagerIntent.SortSelected -> onSortSelected(intent.key)
            AppManagerIntent.UninstallPressed -> onUninstallPressed()
            AppManagerIntent.UninstallConfirmed -> startQueue()
            AppManagerIntent.UninstallDismissed -> setState { copy(confirm = null) }
            is AppManagerIntent.UninstallReturned -> onUninstallReturned(intent.packageName)
            AppManagerIntent.CompletionAnimationFinished ->
                setState { copy(phase = ToolPhase.Ready) }

            is AppManagerIntent.AppInfoRequested ->
                sendEffect(AppManagerEffect.OpenAppInfo(intent.packageName))

            AppManagerIntent.BackPressed -> onBackPressed()
        }
    }

    /** Idempotent: it arrives on every `ON_START`, so `init` observes and does not act (MVI §3). */
    private fun onScreenStarted() {
        launchSafely { markFeatureUsed(FeatureId.AppManager) }
        setState { copy(usageAccess = readUsageAccess()) }
        if (currentState.phase != ToolPhase.Scanning && currentState.uninstalling == null) load()
    }

    private fun readUsageAccess(): UsageAccess =
        if (permissions.isGranted(AppPermission.UsageStats)) {
            UsageAccess.Granted
        } else {
            UsageAccess.Denied
        }

    private fun load() {
        scanJob?.cancel()
        setState { copy(phase = ToolPhase.Scanning, sizedCount = 0, error = null) }
        scanJob = launchSafely(onError = ::onFailure) {
            loadInstalledApps().collect(::reduceLoad)
            setState { withScanFinished() }
        }
    }

    private fun reduceLoad(progress: InstalledAppsProgress) = when (progress) {
        is InstalledAppsProgress.Enumerated ->
            setState { withEnumerated(progress.apps, savedState.restoreSelection()) }

        is InstalledAppsProgress.Sized -> setState { withSized(progress.stats) }
        is InstalledAppsProgress.Failed -> onFailure(progress.error)
    }

    private fun onSortSelected(key: AppSortKey) {
        if (key == AppSortKey.LastUsed && !currentState.lastUsedSortEnabled) return
        setState { withSortSelected(key) }
    }

    private fun saveSelection(reducer: AppManagerState.() -> AppManagerState) {
        setState(reducer)
        savedState.storeSelection(currentState.selectedPackages)
    }

    private fun onUninstallPressed() {
        if (currentState.canUninstall) setState { copy(confirm = uninstallConfirmSpec(selectedCount)) }
    }

    /**
     * Builds the queue and asks about the head. `CleanRequested` carries the bytes the user selected;
     * how many Android actually removes is reported by the summary, not by this event.
     */
    private fun startQueue() {
        analytics.track(
            AnalyticsEvent.CleanRequested(currentState.selectedBytes, currentState.selectedCount),
        )
        setState { withQueueStarted() }
        pump()
    }

    /** One effect, one system dialog, one round trip. Never N `ACTION_DELETE`s in a loop (§5.5). */
    private fun pump() {
        val current = currentState.uninstalling?.current
        if (current != null) {
            sendEffect(AppManagerEffect.RequestUninstall(current))
        } else if (currentState.uninstalling != null) {
            finishQueue()
        }
    }

    /** The re-query is the truth: a result code says the dialog closed, not that the app is gone. */
    private fun onUninstallReturned(packageName: String) {
        launchSafely(onError = ::onFailure) {
            val stillInstalled = uninstallApp(packageName)
            setState { withUninstallReturned(packageName, stillInstalled) }
            pump()
        }
    }

    /**
     * **The result screen is reached only when Android actually removed something.**
     *
     * A queue that ends with `removed` empty is the user having declined every system dialog, and
     * `CleanResultRoute` has no arm that says so: the run renders as `NothingFound` — *"Nothing was
     * found to remove"* — which is a false statement about a list where apps were found, selected
     * and then deliberately kept. Cancelling the last step of a flow must leave the user on the
     * screen they cancelled from, not on a report of a run that did not happen.
     *
     * The selection is written back rather than cleared, so the packages the user declined stay
     * ticked and the retry is one tap. On a partial run this stores exactly what the list still
     * shows selected — `withQueueFinished` has already dropped the removed ones — where the previous
     * `emptySet()` disagreed with the rows on screen.
     */
    private fun finishQueue() {
        val summary = currentState.uninstallSummary()
        val removedAny = currentState.uninstalling?.removed?.isNotEmpty() == true
        setState { withQueueFinished() }
        savedState.storeSelection(currentState.selectedPackages)
        if (removedAny) sendEffect(AppManagerEffect.NavigateToCleanResult(summary))
    }

    private fun onBackPressed() {
        scanJob?.cancel()
        sendEffect(AppManagerEffect.NavigateBack)
    }

    /** Lowers `phase` AND clears `confirm` — both are re-entry guards. */
    private fun onFailure(error: AppError) =
        setState { copy(phase = ToolPhase.Ready, confirm = null, error = error) }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
