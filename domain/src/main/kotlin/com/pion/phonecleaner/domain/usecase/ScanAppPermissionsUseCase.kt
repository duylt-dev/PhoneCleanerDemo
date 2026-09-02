package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.repository.AppPermissionScanRepository
import kotlinx.collections.immutable.ImmutableList

/**
 * "What permissions do other apps hold?" — the whole walk, once
 * (`docs/screens/17-notification-and-permissions.md` §0.3).
 *
 * The `PackageManager` work runs on `dispatchers.default` inside the repository, so the ViewModel names
 * no dispatcher and the scan is a plain child of `viewModelScope` — leaving the screen cancels it
 * mid-walk, which the competitor's unguarded `launch(IO)` cannot do (§4.2).
 *
 * Registered `factoryOf(::ScanAppPermissionsUseCase)` in `domainModule`.
 */
class ScanAppPermissionsUseCase(
    private val repository: AppPermissionScanRepository,
) {
    suspend operator fun invoke(): AppResult<ImmutableList<AppPermissionReport>> = repository.scanAll()
}
