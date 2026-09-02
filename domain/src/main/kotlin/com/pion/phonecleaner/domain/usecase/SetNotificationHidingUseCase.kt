package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.NotificationHidingSettingsStore

/**
 * The two writes of the hiding settings, and the boundary between them
 * (`docs/screens/17-notification-and-permissions.md` §0.3).
 *
 * They are **one** use case with two entry points precisely so the rule that separates them is stated
 * once: the master flag is written by [master] and by nothing else. The competitor writes it from a
 * per-app switch — turning off the last enabled app silently disables the whole feature, with no undo
 * and no message, and the list then vanishes (§2.5).
 *
 * Registered `factoryOf(::SetNotificationHidingUseCase)` in `domainModule`.
 */
class SetNotificationHidingUseCase(
    private val store: NotificationHidingSettingsStore,
) {
    /** One app's row. It never touches the master flag, whatever [enabled] leaves the count at. */
    suspend operator fun invoke(packageName: String, enabled: Boolean): AppResult<Unit> =
        store.setAppEnabled(packageName, enabled)

    /** The master switch. The only write path for it in the app. */
    suspend fun master(enabled: Boolean): AppResult<Unit> = store.setMasterEnabled(enabled)
}
