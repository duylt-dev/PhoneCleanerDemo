package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.AppLockSettingsRepository

/**
 * Whether a newly installed app is offered for locking
 * (`docs/screens/16-app-lock.md` §1.1, §4.1).
 *
 * PENDING OWNER DECISION (4) — the offer's delivery mechanism. The competitor draws it as a
 * `WindowManager` overlay from a `PACKAGE_ADDED` receiver; the design assumes the overlay is dropped
 * and the offer becomes in-app (`docs/screens/16-app-lock.md` §6 item 4). This use case persists the
 * preference and nothing more; no receiver and no overlay is written by this cluster.
 *
 * Registered `factoryOf(::SetLockNewlyInstalledUseCase)` in `domainModule`.
 */
class SetLockNewlyInstalledUseCase(
    private val settings: AppLockSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean): AppResult<Unit> =
        settings.setLockNewlyInstalled(enabled)
}
