package com.pion.phonecleaner.feature.device.batteryinfo

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.ObserveBatteryUseCase

/**
 * `batteryinfo` (`docs/screens/18-device-battery-and-apps.md` §5.2).
 *
 * **The session-lost branch is free here**, which is why this screen is the cluster's simplest: the
 * seed from `batteryscan` only saves the first frame, and if the store is empty — process death, or a
 * deep link — the screen starts `isLoading` and the live `Flow` fills it in from a *sticky* broadcast,
 * which answers immediately. The branch is still explicit rather than incidental (§0.2).
 *
 * A session is **consumed once**: the seed is taken and the slot cleared, so a later return to this
 * route does not render a snapshot from a scan the user has since forgotten about.
 *
 * No dispatcher is named. `BatteryRepository` registers its receiver and parses on
 * `dispatchers.default`, inside the repository.
 */
class BatteryInfoViewModel(
    private val session: DeviceScanSessionStore,
    private val observeBattery: ObserveBatteryUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger,
) : MviViewModel<BatteryInfoState, BatteryInfoIntent, BatteryInfoEffect>(
    BatteryInfoState(snapshot = session.batterySnapshot),
    log,
) {

    init {
        session.batterySnapshot = null

        // Bookkeeping the user did not ask for: fire and forget, and a failure is logged by
        // launchSafely and never surfaced.
        launchSafely { markFeatureUsed(FeatureId.BatteryInfo) }

        observeBattery().collectSafely(
            onError = { error -> setState { copy(error = error) } },
        ) { snapshot ->
            setState { copy(snapshot = snapshot, error = null) }
        }
    }

    override fun onIntent(intent: BatteryInfoIntent) {
        when (intent) {
            BatteryInfoIntent.BackPressed -> sendEffect(BatteryInfoEffect.NavigateBack)
        }
    }
}
