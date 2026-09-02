package com.pion.phonecleaner.feature.device.runningappsscan

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import com.pion.phonecleaner.domain.usecase.ListStoppableAppsUseCase
import com.pion.phonecleaner.domain.usecase.MarkRunningAppsScannedUseCase
import com.pion.phonecleaner.domain.usecase.ReadMemoryUseCase
import com.pion.phonecleaner.feature.device.SCAN_PROGRESS_COMPLETE
import com.pion.phonecleaner.feature.device.tickScanProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * `runningappsscan` (`docs/screens/18-device-battery-and-apps.md` §6.1).
 *
 * **One enumeration, not two.** The competitor throws its result away and the next screen runs the
 * identical `getInstalledPackages` call again — two full passes back to back, the first purely so the
 * wait feels earned. Here the list goes into `DeviceScanSessionStore` and `runningapps` seeds itself
 * from it (§6.4).
 *
 * The memory read and the enumeration are `async` **structural children** running concurrently with
 * the ticker, and `ticker.join()` before the finish is what keeps the beat from being cut short.
 * Both repositories work on `dispatchers.io` internally; this ViewModel names no dispatcher.
 *
 * `markScannedToday()` gets **its own** `launchSafely`, so navigation never waits on it. The
 * competitor writes the same flag with a synchronous `commit()` *inside* the navigation path, between
 * the ad callback and `startActivity` (§6.4).
 *
 * ### PENDING OWNER DECISION 3 — §0.1, UNSETTLED
 *
 * Nothing here reads usage statistics and nothing here asks for `PACKAGE_USAGE_STATS`. If the owner
 * keeps the special access, `ListStoppableAppsUseCase` swaps its source behind the same signature and
 * this file does not change; if the feature is dropped, this file goes with the screen.
 */
class RunningAppsScanViewModel(
    private val session: DeviceScanSessionStore,
    private val readMemory: ReadMemoryUseCase,
    private val listStoppableApps: ListStoppableAppsUseCase,
    private val markRunningAppsScanned: MarkRunningAppsScannedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<RunningAppsScanState, RunningAppsScanIntent, RunningAppsScanEffect>(
    RunningAppsScanState(),
    log,
) {

    init {
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.RunningApps))
        launchSafely(onError = ::onFailure) { runScan() }
    }

    override fun onIntent(intent: RunningAppsScanIntent) {
        when (intent) {
            RunningAppsScanIntent.BackPressed -> if (currentState.isBackBlocked) {
                sendEffect(RunningAppsScanEffect.ShowScanInProgressMessage)
            } else {
                sendEffect(RunningAppsScanEffect.NavigateBack)
            }
        }
    }

    private suspend fun runScan() = coroutineScope {
        val memory = async { readMemory() }
        val apps = async { listStoppableApps() }
        val ticker = launch {
            tickScanProgress { percent -> setState { copy(progress = percent) } }
        }

        // The ring fills as soon as its own read lands, which is well before the beat ends. A
        // failure leaves it null, and null is a shimmer — this screen lives for three seconds and
        // `runningapps` reads memory again the moment it opens, so there is nothing to report here.
        (memory.await() as? AppResult.Success)?.let { result ->
            setState { copy(memory = result.value) }
        }

        ticker.join()

        // The store is written BEFORE the effect is raised, so a navigation on the very next line
        // can never reach a screen with nothing to read. A failed enumeration writes `null`, which
        // is the session-lost branch §0.2 requires: `runningapps` then loads for itself and renders
        // its own error, rather than this screen reporting a failure the user cannot act on.
        session.runningApps = apps.await().getOrNull()

        setState {
            copy(
                progress = SCAN_PROGRESS_COMPLETE,
                isFinished = true,
                isBackBlocked = false,
            )
        }
        launchSafely { markRunningAppsScanned() }
        sendEffect(RunningAppsScanEffect.NavigateToRunningApps)
    }

    /**
     * Lowers `isBackBlocked` as well as setting `error`. `launchSafely`'s catch path reaches neither
     * `AppResult` arm, so a flag it does not lower stays raised — and a screen whose Back is refused
     * forever is the stuck state `LLM.md` §9's fourth test category exists for.
     */
    private fun onFailure(error: AppError) {
        setState { copy(isBackBlocked = false, error = error) }
    }
}
