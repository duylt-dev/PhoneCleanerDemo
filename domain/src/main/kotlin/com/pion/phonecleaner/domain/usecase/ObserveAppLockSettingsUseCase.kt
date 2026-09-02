package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.applock.AppLockSettings
import com.pion.phonecleaner.domain.repository.AppLockSettingsRepository
import kotlinx.coroutines.flow.Flow

/**
 * The two App Lock preferences on one upstream (`docs/screens/16-app-lock.md` §4.2).
 *
 * Read by two screens: the settings page renders both switches, and the App Lock home renders
 * `lockNewlyInstalled`. One flow, so the two can never disagree.
 *
 * Registered `factoryOf(::ObserveAppLockSettingsUseCase)` in `domainModule`.
 */
class ObserveAppLockSettingsUseCase(
    private val settings: AppLockSettingsRepository,
) {
    operator fun invoke(): Flow<AppLockSettings> = settings.observeSettings()
}
