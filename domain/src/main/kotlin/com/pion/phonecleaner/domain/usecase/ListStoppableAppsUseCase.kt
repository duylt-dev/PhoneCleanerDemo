package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.domain.repository.RunningAppsRepository
import kotlinx.collections.immutable.ImmutableList

/**
 * The list both running-apps screens show
 * (`docs/screens/18-device-battery-and-apps.md` §1.1, §6.4).
 *
 * **It is enumerated once.** The competitor runs the identical `getInstalledPackages` pass on the
 * scan screen, throws the result away, and runs it again on the list screen — two full passes back to
 * back, the first purely so the wait feels earned. Here the scan writes its result to
 * `DeviceScanSessionStore` and the list screen seeds from it.
 */
class ListStoppableAppsUseCase(
    private val runningApps: RunningAppsRepository,
) {
    suspend operator fun invoke(): AppResult<ImmutableList<RunningApp>> = runningApps.stoppableApps()
}
