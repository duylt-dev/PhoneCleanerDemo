package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.domain.model.device.UsageAccessState
import kotlinx.collections.immutable.ImmutableList

/**
 * The apps this device lets us hand to system Settings
 * (`docs/screens/18-device-battery-and-apps.md` §1.1, §6). Replaces `cd.j.b()`; declared once, in
 * `deviceDataModule` (§8).
 *
 * It is **not** `InstalledAppsRepository` (`coreDataModule`, the App Manager's rich list) — §8 keeps
 * the two apart deliberately, because they answer different questions and one is not a superset of
 * the other.
 *
 * ### The pending owner decision lives behind this interface, not in front of it
 *
 * `docs/screens/18-device-battery-and-apps.md` §0.1 / `docs/system-architecture.md` §10.1 **P1** is
 * unsettled. [stoppableApps] today is the `PackageManager` enumeration the competitor actually runs
 * — third-party, not already stopped. If the owner keeps the special access, this one method swaps to
 * `UsageStatsManager.queryEvents` over a recent window and **no contract, no ViewModel and no screen
 * changes**. [usageAccess] is what the screens render the ungranted path from meanwhile.
 */
interface RunningAppsRepository {

    suspend fun stoppableApps(): AppResult<ImmutableList<RunningApp>>

    /**
     * Re-reads `ApplicationInfo.FLAG_STOPPED` for one package.
     *
     * This is the **only** way this app learns whether a force-stop happened: the Settings deep link
     * reports nothing about what the user did, and `killBackgroundProcesses` — which the competitor
     * calls — needs a permission it does not declare, returns `void`, and sits inside an empty
     * `catch`. A row is marked stopped only when the platform says so (§6.5).
     */
    suspend fun isStopped(packageName: String): AppResult<Boolean>

    /** Whether `PACKAGE_USAGE_STATS` is held. See [UsageAccessState] for the open decision. */
    suspend fun usageAccess(): UsageAccessState
}
