package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.RunningAppsRepository

/**
 * Did the force-stop actually happen? (`docs/screens/18-device-battery-and-apps.md` §1.1, §6.5.)
 *
 * Called on `ScreenResumed` for the one package we handed to system Settings, and **only** that one.
 * A `true` marks the row stopped; a `false` says nothing at all — the row simply stays. The app never
 * claims a result it did not observe, which is precisely what both of the competitor's Stop actions
 * do: `killBackgroundProcesses` returns `void` inside an empty `catch`, and the Settings deep link
 * reports nothing back.
 *
 * *(Inferred, medium-high: the `FLAG_STOPPED` read is established — it is the same bit the
 * enumeration already filters on — but the post-force-stop timing is not tested on a device.)*
 */
class VerifyAppStoppedUseCase(
    private val runningApps: RunningAppsRepository,
) {
    suspend operator fun invoke(packageName: String): AppResult<Boolean> =
        runningApps.isStopped(packageName)
}
