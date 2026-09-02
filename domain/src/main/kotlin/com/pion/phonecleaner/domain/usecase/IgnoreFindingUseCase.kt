package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.SecurityScanRepository

/**
 * Keeps one finding off the list without pretending it was removed
 * (`docs/screens/15-antivirus.md` §2.2).
 *
 * **Additive, not a port.** The competitor offers exactly one action per row — delete or uninstall —
 * so a false positive on an app the user trusts is unmanageable and the row returns on every scan
 * (§2.5).
 *
 * Thin by construction: the rule ("what counts as a finding") is the repository's, and there is
 * nothing here for a policy to decide. It exists as a use case because §0.6 names it and because the
 * screen depends on the *action*, not on the port.
 *
 * Registered `factoryOf(::IgnoreFindingUseCase)` in `domainModule` — reported, not added here.
 */
class IgnoreFindingUseCase(
    private val repository: SecurityScanRepository,
) {
    suspend operator fun invoke(md5: String): AppResult<Unit> = repository.ignore(md5)
}
