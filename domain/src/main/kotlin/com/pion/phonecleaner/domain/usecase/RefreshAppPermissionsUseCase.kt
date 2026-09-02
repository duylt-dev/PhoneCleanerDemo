package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.repository.AppPermissionScanRepository

/**
 * Re-reads **one** package after the user came back from its system settings page
 * (`docs/screens/17-notification-and-permissions.md` §0.3).
 *
 * One call, one walk. The competitor runs three independent paths for one return from Settings —
 * three `PackageManager` walks, three notify calls, and a detail sheet whose count can disagree with
 * the row behind it (§4.5).
 *
 * `null` means the package now holds nothing worth reporting, which the caller renders by **removing**
 * the row, not by leaving a stale one.
 *
 * Registered `factoryOf(::RefreshAppPermissionsUseCase)` in `domainModule`.
 */
class RefreshAppPermissionsUseCase(
    private val repository: AppPermissionScanRepository,
) {
    suspend operator fun invoke(packageName: String): AppResult<AppPermissionReport?> =
        repository.refresh(packageName)
}
