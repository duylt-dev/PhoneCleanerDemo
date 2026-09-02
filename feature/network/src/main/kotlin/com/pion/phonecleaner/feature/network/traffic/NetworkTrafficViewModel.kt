package com.pion.phonecleaner.feature.network.traffic

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.network.TrafficFilter
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.domain.model.network.TrafficReport
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.GetTrafficReportUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `traffic` (`docs/screens/19-network-and-speed-test.md` §1.2).
 *
 * **No `android.*` and no Compose import.** The competitor's equivalents — `S()`, `P()`, `Y()`,
 * `m0()`, `j0()`, `k0()` — are `public final` methods on an Activity that start Intents, so every one
 * of them is callable from anywhere and none of them is testable. Here [onIntent] is the only public
 * method and every platform hand-off is an Effect the Route performs.
 *
 * **No dispatcher is named here.** `NetworkTrafficRepository` does its own `withContext` behind
 * `DispatcherProvider`; the competitor picks one at each of its two call sites, so the same query
 * runs on a different thread depending on which screen asked (`LLM.md` §6.5).
 */
class NetworkTrafficViewModel(
    private val savedState: SavedStateHandle,
    private val permissions: PermissionRepository,
    private val getTrafficReport: GetTrafficReportUseCase,
    private val installedApps: InstalledAppsRepository,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger,
) : MviViewModel<NetworkTrafficState, NetworkTrafficIntent, NetworkTrafficEffect>(
    NetworkTrafficState(
        period = savedState.restorePeriod(),
        filter = savedState.restoreFilter(),
    ),
    log,
) {

    /** ONE query at a time, cancel-and-replace: a triple-tap on the period tabs must land in order. */
    private var reportJob: Job? = null

    override fun onIntent(intent: NetworkTrafficIntent) {
        when (intent) {
            NetworkTrafficIntent.ScreenStarted -> onScreenStarted()
            NetworkTrafficIntent.GrantAccessPressed ->
                sendEffect(NetworkTrafficEffect.OpenUsageAccessSettings)

            is NetworkTrafficIntent.SelectPeriod -> onSelectPeriod(intent.period)
            is NetworkTrafficIntent.SelectFilter -> onSelectFilter(intent.filter)
            is NetworkTrafficIntent.StopPressed -> onStopPressed(intent.packageName)
            NetworkTrafficIntent.DonePressed,
            NetworkTrafficIntent.BackPressed,
            -> onLeave()

            NetworkTrafficIntent.RetryPressed -> load(currentState.period)
        }
    }

    /**
     * Idempotent, and raised on every `ON_START` — `init` observes and does not act (MVI §3). It is
     * both the grant re-check the competitor does in `onResume` and the fix for its stale list: a row
     * the user has just been to App info for is re-read on the way back.
     */
    private fun onScreenStarted() {
        if (!permissions.isGranted(AppPermission.UsageStats)) {
            reportJob?.cancel()
            setState { withAccessMissing() }
            return
        }
        launchSafely { markFeatureUsed(FeatureId.NetworkTraffic) } // bookkeeping: fire and forget
        when {
            // A second ON_START during a query does not restart it: LifecycleStartEffect fires on
            // every return to the foreground, and cancel-and-replace here would throw away a query
            // that is already fetching exactly what the screen wants.
            currentState.phase == NetworkTrafficState.Phase.Scanning -> Unit

            currentState.report == null || currentState.stopRequestedFor != null ->
                load(currentState.period)

            else -> setState { copy(phase = NetworkTrafficState.Phase.Ready) }
        }
    }

    private fun onSelectPeriod(period: TrafficPeriod) {
        if (period == currentState.period) return
        setState { copy(period = period) }
        savedState.storePeriod(period)
        load(period)
    }

    /** A filter change re-derives `rows`; it never re-queries. One query per period, not per tap. */
    private fun onSelectFilter(filter: TrafficFilter) {
        if (filter == currentState.filter) return
        setState { copy(filter = filter) }
        savedState.storeFilter(filter)
    }

    private fun load(period: TrafficPeriod) {
        reportJob?.cancel()
        setState { withLoadStarted() }
        reportJob = launchSafely(onError = { setState { withFailure(it) } }) {
            val result = withTimeoutOrNull(QueryTimeoutMillis) { getTrafficReport(period) }
            if (period != currentState.period) return@launchSafely // a later tap already won
            when (result) {
                null -> setState { withFailure(AppError.Unexpected(TIMEOUT_DETAIL)) }
                is AppResult.Failure -> setState { withFailure(result.error) }
                is AppResult.Success -> publish(result.value)
            }
        }
    }

    /**
     * Labels come from `InstalledAppsRepository` — the port `docs/system-architecture.md` §4.1 chose
     * over this cluster's own `AppInfoRepository` proposal. A failure to read them is carried as the
     * state's error rather than swallowed: without labels every row is dropped, and a list that
     * empties itself without saying why is the defect this screen exists to not repeat.
     */
    private suspend fun publish(report: TrafficReport) {
        when (val installed = installedApps.installedApps(includeWithoutLauncher = true)) {
            is AppResult.Failure ->
                setState { withReport(report, persistentMapOf(), installed.error) }

            is AppResult.Success ->
                setState { withReport(report, labelsFor(report, installed.value), null) }
        }
    }

    private fun labelsFor(
        report: TrafficReport,
        installed: List<InstalledApp>,
    ): ImmutableMap<String, String> {
        val wanted = report.apps.flatMapTo(HashSet()) { it.packageNames }
        return installed.asSequence()
            .filter { it.packageName in wanted && it.label.isNotBlank() }
            .associate { it.packageName to it.label }
            .toImmutableMap()
    }

    /**
     * The row is marked, the system page is opened, and `ON_START` re-queries on the way back so the
     * user sees whether anything changed. No overlay is drawn over the system Settings app: a
     * background activity start over another app is restricted from Android 10 and largely blocked on
     * 14 (§1.5 D1/D5).
     */
    private fun onStopPressed(packageName: String) {
        setState { withStopRequested(packageName) }
        sendEffect(NetworkTrafficEffect.OpenAppDetails(packageName))
    }

    private fun onLeave() {
        reportJob?.cancel()
        sendEffect(NetworkTrafficEffect.NavigateBack)
    }

    override fun onCleared() {
        super.onCleared()
        reportJob?.cancel()
    }

    private companion object {
        /**
         * A bounded wait, so the indicator comes down even when `system_server` never answers. The
         * competitor bounds nothing on any screen in this cluster (§1.5 D2).
         */
        const val QueryTimeoutMillis = 15_000L

        /**
         * `AppError` has no `Timeout` arm and `core/common/error/AppError.kt` belongs to another
         * owner, so a timeout is reported as `Unexpected` with the detail in the log. The user-facing
         * string is the generic one; it is not a false statement, only a vague one.
         */
        const val TIMEOUT_DETAIL = "traffic query exceeded ${QueryTimeoutMillis}ms"
    }
}
