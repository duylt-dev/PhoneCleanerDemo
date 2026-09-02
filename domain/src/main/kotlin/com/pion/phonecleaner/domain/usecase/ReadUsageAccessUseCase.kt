package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.device.UsageAccessState
import com.pion.phonecleaner.domain.repository.RunningAppsRepository

/**
 * Whether the `PACKAGE_USAGE_STATS` special access is held.
 *
 * ### PENDING OWNER DECISION — `docs/screens/18-device-battery-and-apps.md` §0.1,
 * `docs/system-architecture.md` §10.1 **P1**. Unsettled.
 *
 * This use case is the seam. Today its answer only decides whether the running-apps screens render
 * the rationale surface; nothing reads usage statistics, and the permission is declared in no
 * manifest. If the owner ships the grant, `RunningAppsRepository.stoppableApps` changes source behind
 * it and this call becomes a real precondition. If the owner drops the screen, this file and the
 * route go together. Neither outcome is closed off by anything written here.
 */
class ReadUsageAccessUseCase(
    private val runningApps: RunningAppsRepository,
) {
    suspend operator fun invoke(): UsageAccessState = runningApps.usageAccess()
}
