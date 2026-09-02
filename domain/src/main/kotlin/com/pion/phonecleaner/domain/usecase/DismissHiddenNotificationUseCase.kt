package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.NotificationCleanerRepository

/**
 * Removes one row (`docs/screens/17-notification-and-permissions.md` §0.3).
 *
 * The row disappears because `observeHidden()` re-emits, never because a ViewModel edited a list — and
 * it runs only **after** the app launch succeeded, so a missing launcher intent leaves the row where it
 * is instead of silently consuming it (§3.5).
 *
 * Registered `factoryOf(::DismissHiddenNotificationUseCase)` in `domainModule`.
 */
class DismissHiddenNotificationUseCase(
    private val repository: NotificationCleanerRepository,
) {
    suspend operator fun invoke(key: String): AppResult<Unit> = repository.dismiss(key)
}
