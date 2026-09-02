package com.pion.phonecleaner.feature.device.devicestatusscan

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import com.pion.phonecleaner.domain.usecase.ReadDeviceMetricsUseCase
import com.pion.phonecleaner.feature.device.SCAN_PROGRESS_COMPLETE
import com.pion.phonecleaner.feature.device.tickScanProgress
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * `devicestatusscan` (`docs/screens/18-device-battery-and-apps.md` §2.2).
 *
 * **The wait does the reading.** In the competitor these three seconds have no work behind them at
 * all; here the five metric reads run as structural children *during* the beat and land in
 * `DeviceScanSessionStore`, so `devicestatusdetail` opens on a filled page instead of re-reading
 * everything the scan just read — the discard-and-redo defect §6.4 records for the running-apps pair.
 *
 * The beat is a **floor**: `ticker.join()` before the finish guarantees it is never cut short, and
 * `reading.await()` after it means a slow device takes as long as its reading takes. That is §2.5's
 * `max(elapsed, 3.seconds)`.
 *
 * **`markFeatureUsed` is deliberately absent.** The competitor records device-status usage on the
 * *detail* screen and that is the right place: a scan is a transition, not a use (§2.2). The
 * `FeatureOpened` arm fires here, on the route every entry point lands on, and on this screen only —
 * firing it on both would double every device-status open in the funnel.
 *
 * **Cancellation is structural.** `onCleared` cancels `viewModelScope`, which cancels the
 * `coroutineScope` and both children. There is no `Job` field to forget, and the ticker is bounded,
 * so nothing can outlive the ViewModel even if a cancel is missed — against a `ValueAnimator` on an
 * Activity field that has to be cancelled by hand in `onDestroy` and restarts on every rotation.
 *
 * **No dispatcher is named.** `DeviceMetricsRepository` runs its `/proc`, `/sys` and `StatFs` reads
 * on `dispatchers.io` internally, and `ReadDeviceMetricsUseCase` owns the `StatFs` timeout.
 */
class DeviceStatusScanViewModel(
    private val session: DeviceScanSessionStore,
    private val readDeviceMetrics: ReadDeviceMetricsUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<DeviceStatusScanState, DeviceStatusScanIntent, DeviceStatusScanEffect>(
    DeviceStatusScanState(),
    log,
) {

    init {
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.DeviceStatus))
        launchSafely(onError = ::onFailure) { runScan() }
    }

    override fun onIntent(intent: DeviceStatusScanIntent) {
        when (intent) {
            DeviceStatusScanIntent.BackPressed -> if (currentState.isBackBlocked) {
                sendEffect(DeviceStatusScanEffect.ShowScanInProgressMessage)
            } else {
                sendEffect(DeviceStatusScanEffect.NavigateBack)
            }
        }
    }

    private suspend fun runScan() = coroutineScope {
        val reading = async { readDeviceMetrics() }
        val ticker = launch {
            tickScanProgress { percent -> setState { copy(progress = percent) } }
        }

        ticker.join()
        session.deviceMetrics = reading.await()

        setState {
            copy(
                progress = SCAN_PROGRESS_COMPLETE,
                isFinished = true,
                isBackBlocked = false,
            )
        }
        sendEffect(DeviceStatusScanEffect.NavigateToDetail)
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
