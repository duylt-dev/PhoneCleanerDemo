package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import com.pion.phonecleaner.domain.repository.NotificationHidingSettingsStore
import kotlinx.coroutines.flow.Flow

/**
 * The master switch and the per-app rows on **one** upstream
 * (`docs/screens/17-notification-and-permissions.md` §0.3).
 *
 * Read by three screens — `gate` needs only the master flag, `hidingsettings` renders both halves, and
 * `hiddenlist` renders the paused banner from the master flag. One flow, so no screen can show a master
 * switch that disagrees with its own rows (§2.2).
 *
 * Registered `factoryOf(::ObserveNotificationHidingSettingsUseCase)` in `domainModule`.
 */
class ObserveNotificationHidingSettingsUseCase(
    private val store: NotificationHidingSettingsStore,
) {
    operator fun invoke(): Flow<NotificationHidingSettings> = store.observe()
}
