package com.pion.phonecleaner.feature.device.devicestatusdetail

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ObserveBatteryUseCase
import com.pion.phonecleaner.domain.usecase.ReadDeviceMetricsUseCase

/**
 * `devicestatusdetail` (`docs/screens/18-device-battery-and-apps.md` §3.2).
 *
 * **This is where device-status usage is recorded**, not on the scan screen: a scan is a transition,
 * not a use (§2.2).
 *
 * **`AnalyticsEvent.FeatureOpened` fires on `devicestatusscan`, not here**, even though §3.2's `init`
 * list mentions an `analytics.log(…)`. The scan is the route every entry point lands on, so firing
 * the arm on both screens would double every device-status open in the funnel — and a denominator
 * that counts one visit twice is worse than one that is late. `markFeatureUsed` stays here, because
 * *using* the feature is what this screen is.
 *
 * The session seed is `devicestatusscan`'s work, taken once and cleared. **The session-lost branch is
 * explicit** (§0.2): with an empty store the screen simply loads for itself, which is also what every
 * resume does, so the two paths are one function.
 *
 * The battery card collects the **same** `Flow` `batteryinfo` collects, so it ticks live here too —
 * which the competitor's snapshot-in-`onCreate` detail screen does not do.
 *
 * **Cancellation is structural.** `onCleared` cancels `viewModelScope`, which cancels the
 * `coroutineScope` inside `ReadDeviceMetricsUseCase` and every `async` under it. There is no `Job`
 * field to forget.
 */
class DeviceStatusDetailViewModel(
    private val session: DeviceScanSessionStore,
    private val readDeviceMetrics: ReadDeviceMetricsUseCase,
    private val observeBattery: ObserveBatteryUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger,
) : MviViewModel<DeviceStatusDetailState, DeviceStatusDetailIntent, DeviceStatusDetailEffect>(
    DeviceStatusDetailState(),
    log,
) {

    init {
        launchSafely { markFeatureUsed(FeatureId.DeviceStatus) }

        session.deviceMetrics?.let { seed ->
            session.deviceMetrics = null
            setState { withMetrics(seed) }
        }

        observeBattery().collectSafely(
            onError = { error -> setState { copy(error = error) } },
        ) { snapshot ->
            setState { copy(battery = snapshot) }
        }

        load()
    }

    override fun onIntent(intent: DeviceStatusDetailIntent) {
        when (intent) {
            // Cheap, and it is what a user expects after coming back from the junk cleaner having
            // freed two gigabytes. The competitor's page is a snapshot taken in onCreate.
            DeviceStatusDetailIntent.ScreenResumed -> load()
            DeviceStatusDetailIntent.RetryTapped -> load()
            DeviceStatusDetailIntent.CheckMemoryTapped ->
                sendEffect(DeviceStatusDetailEffect.NavigateToRunningApps)

            DeviceStatusDetailIntent.CheckStorageTapped ->
                sendEffect(DeviceStatusDetailEffect.NavigateToJunkClean)

            DeviceStatusDetailIntent.CheckBatteryTapped ->
                sendEffect(DeviceStatusDetailEffect.NavigateToBattery)

            DeviceStatusDetailIntent.BackPressed ->
                sendEffect(DeviceStatusDetailEffect.NavigateBack)
        }
    }

    /**
     * `onError` lowers `isLoading` as well as setting `error`: the `AppResult` arms are not the only
     * outcomes, and `launchSafely`'s third path reaches neither of them (MVI §1). A spinner left up
     * by a thrown exception is the stuck state `LLM.md` §9's fourth test category exists for.
     *
     * The concurrency and the `StatFs` timeout live inside `ReadDeviceMetricsUseCase`, so this
     * ViewModel names no dispatcher and holds no bound of its own.
     */
    private fun load() {
        setState { copy(isLoading = true, error = null) }
        launchSafely(onError = { error -> setState { copy(isLoading = false, error = error) } }) {
            val metrics = readDeviceMetrics()
            setState { withMetrics(metrics) }
        }
    }
}
