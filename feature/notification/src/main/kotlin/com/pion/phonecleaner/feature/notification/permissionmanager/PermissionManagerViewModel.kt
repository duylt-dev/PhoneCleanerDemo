package com.pion.phonecleaner.feature.notification.permissionmanager

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.repository.FeatureStatsRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.GroupAppsByPermissionUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.RefreshAppPermissionsUseCase
import com.pion.phonecleaner.domain.usecase.ScanAppPermissionsUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * One ViewModel for three tabs (`docs/screens/17-notification-and-permissions.md` §4.2).
 *
 * **Nothing is mutated in place, anywhere.** `Tilhassl.l()` mutates the objects its `LiveData` holds and
 * never re-posts; Compose would never recompose from that, and even in the XML app it works only
 * because a second path mutates the same objects again (§4.5). Here `apps` is replaced by a new list
 * and `groups` is recomputed from it **in the same `setState`**, so the two axes can never disagree.
 *
 * **No dispatcher is named and nothing here imports `android.*`.** The `PackageManager` walk runs on
 * `dispatchers.default` inside `AppPermissionScanRepository`; `PermissionRepository` answers on the
 * caller's thread by design, from one cheap `system_server` round trip per constant.
 *
 * ANALYTICS — this ViewModel takes no `AnalyticsRepository`. The three tab events are verified
 * (`ev 102105` / `102106` / `102107` for tabs 0 / 1 / 2,
 * `docs/reverse-engineering/17-notification-and-permissions.md:420` and the correction at :768), but
 * `AnalyticsEvent` has no arm that can carry them: an arm needs a row in
 * `domain/repository/AnalyticsRepository.kt` **and** two branches in
 * `data/analytics/RemoteAnalyticsRepository.kt`, neither of which this cluster owns. The arm is
 * reported to the owner rather than invented, and [onTabSelected] is where its one call site goes.
 */
class PermissionManagerViewModel(
    savedStateHandle: SavedStateHandle,
    private val scanPermissions: ScanAppPermissionsUseCase,
    private val refreshApp: RefreshAppPermissionsUseCase,
    private val groupApps: GroupAppsByPermissionUseCase,
    private val permissions: PermissionRepository,
    private val featureStats: FeatureStatsRepository,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger = AppLogger.NoOp,
) : MviViewModel<PermissionManagerState, PermissionManagerIntent, PermissionManagerEffect>(
    PermissionManagerState(selectedTab = savedStateHandle.permissionTab()),
    log,
) {

    init {
        scan()
        launchSafely { markFeatureUsed(FeatureId.PermissionManager) }
    }

    override fun onIntent(intent: PermissionManagerIntent) {
        when (intent) {
            PermissionManagerIntent.ScanAnimationFinished -> setState { copy(isScanning = false) }
            is PermissionManagerIntent.TabSelected -> onTabSelected(intent)
            is PermissionManagerIntent.AppRowTapped ->
                setState { copy(detailSheet = DetailSheet(intent.packageName)) }

            is PermissionManagerIntent.GroupToggled -> setState { toggleGroup(intent.id) }
            is PermissionManagerIntent.DetailSectionToggled ->
                setState { toggleSection(intent.section) }
            PermissionManagerIntent.DetailDismissed -> setState { copy(detailSheet = null) }
            PermissionManagerIntent.ManageTapped -> onManage()
            is PermissionManagerIntent.SpecialAccessTapped ->
                sendEffect(PermissionManagerEffect.OpenSpecialAccessSettings(intent.access))

            PermissionManagerIntent.ScreenResumed -> onResumed()
            PermissionManagerIntent.RetryTapped -> onRetry()
            PermissionManagerIntent.BackPressed -> onBack()
        }
    }

    /**
     * `apps` and `groups` in **one** `setState`. The counter is written at the end of a successful scan,
     * not in a destroy callback: a process killed while this screen is open never updates a number three
     * out-of-app surfaces read (§4.5).
     */
    private fun scan() {
        launchSafely(onError = ::onScanFailed) {
            when (val result = scanPermissions()) {
                is AppResult.Failure -> onScanFailed(result.error)
                is AppResult.Success -> {
                    applyApps(result.value)
                    featureStats.setSensitiveAppCount(currentState.sensitiveAppCount)
                }
            }
            readSpecialAccess()
        }
    }

    private fun applyApps(apps: ImmutableList<AppPermissionReport>) {
        setState { copy(apps = apps, groups = groupApps(apps), error = null) }
    }

    /**
     * `isScanning` is lowered here as well as by `ScanAnimationFinished`: a failure has no animation to
     * wait for, and MVI §1 requires `onError` to lower every flag the call raised.
     */
    private fun onScanFailed(error: AppError) {
        setState { copy(isScanning = false, error = error) }
        sendEffect(PermissionManagerEffect.ShowMessage(error))
    }

    private fun onTabSelected(intent: PermissionManagerIntent.TabSelected) {
        setState { copy(selectedTab = intent.tab) }
        // UNKNOWN — the AnalyticsEvent arm that carries ev 102105 / 102106 / 102107. Looked for an arm
        // able to hold a tab index or a raw wire id in domain/repository/AnalyticsRepository.kt (six
        // arms: FeatureOpened, ExitOfferShown, CleanRequested, JunkScanFinished, JunkScanCancelled,
        // JunkCleanFinished) and in the batch-1 addendum of the shared API digest. There is none, and
        // this cluster owns neither that file nor RemoteAnalyticsRepository.kt. Reported, not invented.
    }

    private fun onManage() {
        val packageName = currentState.detailSheet?.packageName ?: return
        setState { copy(awaitingRefreshFor = packageName) }
        sendEffect(PermissionManagerEffect.OpenAppSettings(packageName))
    }

    /**
     * One `launchSafely`, and the special-access re-read runs **every** time.
     *
     * `isJumpSpecialAccessSettingsAndResume` — a public setter on the competitor's ViewModel — is
     * deleted: the six grants are six cheap platform calls, and a flag guarding them is a second thing
     * to keep in step for no measurable saving (§4.1).
     */
    private fun onResumed() {
        launchSafely(
            onError = { error ->
                setState { copy(awaitingRefreshFor = null, error = error) }
                sendEffect(PermissionManagerEffect.ShowMessage(error))
            },
        ) {
            val pending = currentState.awaitingRefreshFor
            if (pending != null) {
                setState { copy(awaitingRefreshFor = null) }
                refreshOne(pending)
            }
            readSpecialAccess()
        }
    }

    /** Replace-or-remove, producing a **new list**; `groups` is recomputed from it in the same write. */
    private suspend fun refreshOne(packageName: String) {
        val refreshed = when (val result = refreshApp(packageName)) {
            is AppResult.Failure -> {
                setState { copy(error = result.error) }
                sendEffect(PermissionManagerEffect.ShowMessage(result.error))
                return
            }

            is AppResult.Success -> result.value
        }
        val updated = currentState.apps
            .mapNotNull { if (it.packageName == packageName) refreshed else it }
            .toImmutableList()
        applyApps(updated)
        // The sheet is dropped when the app no longer holds anything, so it cannot outlive its subject.
        if (refreshed == null) setState { copy(detailSheet = null) }
        featureStats.setSensitiveAppCount(currentState.sensitiveAppCount)
    }

    /** Six reads, on every resume. See [specialAccessRows]. */
    private fun readSpecialAccess() {
        val rows = specialAccessRows(permissions)
        setState { copy(specialAccess = rows) }
    }

    private fun onRetry() {
        if (currentState.error == null) return
        setState { copy(isScanning = true, error = null) }
        scan()
    }

    private fun onBack() {
        if (currentState.isScanning) {
            sendEffect(PermissionManagerEffect.ShowScanInProgressMessage)
        } else {
            sendEffect(PermissionManagerEffect.NavigateBack)
        }
    }

}
