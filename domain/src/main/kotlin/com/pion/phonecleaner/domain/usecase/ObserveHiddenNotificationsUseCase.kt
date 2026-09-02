package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.notification.HiddenNotification
import com.pion.phonecleaner.domain.repository.NotificationCleanerRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The hidden list, straight from Room (`docs/screens/17-notification-and-permissions.md` §0.3).
 *
 * **This flow IS the refresh mechanism.** The competitor posts on `LiveEventBus`
 * (`flux_refresh_noti_list`) with no payload, so the screen answers by re-reading the whole store from
 * disk and reloading every icon — once per intercepted notification, driven by other apps' rate (§3.5).
 *
 * Registered `factoryOf(::ObserveHiddenNotificationsUseCase)` in `domainModule`.
 */
class ObserveHiddenNotificationsUseCase(
    private val repository: NotificationCleanerRepository,
) {
    operator fun invoke(): Flow<ImmutableList<HiddenNotification>> = repository.observeHidden()
}
