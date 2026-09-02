package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.ScanBadgeRepository

/**
 * Records that the running-apps scan ran today, which clears the home tile's red dot
 * (`docs/screens/18-device-battery-and-apps.md` §1.1, §6.4).
 *
 * Fire and forget, in its own `launchSafely`: this is bookkeeping the user did not ask for, so a
 * failure is logged and never surfaced, and **navigation does not wait on it**. The competitor's
 * equivalent is a `commit()` on the calling thread, in the navigation path.
 */
class MarkRunningAppsScannedUseCase(
    private val badges: ScanBadgeRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> = badges.markScannedToday()
}
