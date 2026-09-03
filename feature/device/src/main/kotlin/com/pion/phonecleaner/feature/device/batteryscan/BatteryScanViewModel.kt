package com.pion.phonecleaner.feature.device.batteryscan

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.device.BatteryCheck
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import com.pion.phonecleaner.domain.usecase.ObserveBatteryUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.milliseconds

/**
 * `batteryscan` (`docs/screens/18-device-battery-and-apps.md` §4.2).
 *
 * **The steps now check the things they name.** In the competitor no battery API is touched
 * on this screen at all — 4 500 ms of theatre in front of a read that takes microseconds. The rhythm
 * is kept, because it is the brand's; the lying is not.
 *
 * The real read runs as an `async` **structural child** concurrently with the step schedule, so the
 * beat is the floor and not the cost. `onCleared` cancels `viewModelScope`, which takes the `async`
 * with it — against seven `Handler.postDelayed`s, each of which re-checks `isDestroyed || isFinishing`
 * and depends on one `removeCallbacksAndMessages(null)` to not touch a dead binding.
 *
 * **All timeline state is in `BatteryScanState`.** The competitor's survives nothing and relies on
 * `screenOrientation="portrait"`; here a rotation is free, and the coroutine does not restart.
 */
class BatteryScanViewModel(
    private val session: DeviceScanSessionStore,
    private val observeBattery: ObserveBatteryUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<BatteryScanState, BatteryScanIntent, BatteryScanEffect>(BatteryScanState(), log) {

    init {
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.BatteryInfo))
        launchSafely(onError = ::onFailure) { runScan() }
    }

    override fun onIntent(intent: BatteryScanIntent) {
        when (intent) {
            BatteryScanIntent.BackPressed -> if (currentState.isBackBlocked) {
                sendEffect(BatteryScanEffect.ShowScanInProgressMessage)
            } else {
                sendEffect(BatteryScanEffect.NavigateBack)
            }
        }
    }

    /**
     * `first()` on the battery feed, not a bespoke one-shot read: the source is a **sticky**
     * broadcast, so it answers immediately and the `async` is finished long before the steps are.
     * Sharing the feed is also what keeps `batteryscan` and `batteryinfo` from drifting apart.
     */
    private suspend fun runScan() = coroutineScope {
        val reading = async { observeBattery().first() }

        delay(FIRST_STEP_DELAY)
        BatteryCheck.entries.forEach { check ->
            setState { copy(rows = rows.markRunning(check)) }
            delay(STEP_INTERVAL)
        }
        delay(SETTLE_DELAY)

        session.batterySnapshot = reading.await()
        setState { copy(rows = rows.markAllDone(), isFinished = true, isBackBlocked = false) }
        sendEffect(BatteryScanEffect.NavigateToBatteryInfo)
    }

    /**
     * Lowers `isBackBlocked` as well as setting `error`. `launchSafely`'s catch path reaches neither
     * of the `AppResult` arms, so a flag it does not lower stays raised — and a screen whose Back is
     * refused forever is the stuck state `LLM.md` §9's fourth test category exists for.
     */
    private fun onFailure(error: com.pion.phonecleaner.core.common.error.AppError) {
        setState { copy(isBackBlocked = false, error = error) }
    }

    /**
     * The competitor's timeline, read off `i * 550 + 300` and its trailing `postDelayed(…, 500)`:
     * 300 + 5 × 550 + 500 = **4 500 ms** total.
     */
    private companion object {
        val FIRST_STEP_DELAY = 300.milliseconds
        val STEP_INTERVAL = 550.milliseconds
        val SETTLE_DELAY = 500.milliseconds
    }
}
