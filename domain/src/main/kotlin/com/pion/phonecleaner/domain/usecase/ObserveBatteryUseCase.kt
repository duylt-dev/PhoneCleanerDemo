package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.repository.BatteryRepository
import kotlinx.coroutines.flow.Flow

/**
 * The battery feed, for the three screens that render it
 * (`docs/screens/18-device-battery-and-apps.md` §1.1): `batteryscan` takes its `first()`,
 * `batteryinfo` collects it, and `devicestatusdetail`'s battery card collects the very same one.
 */
class ObserveBatteryUseCase(
    private val battery: BatteryRepository,
) {
    operator fun invoke(): Flow<BatterySnapshot> = battery.observe()
}
