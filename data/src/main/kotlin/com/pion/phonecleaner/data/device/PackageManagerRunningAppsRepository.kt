package com.pion.phonecleaner.data.device

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.domain.model.device.UsageAccessState
import com.pion.phonecleaner.domain.repository.RunningAppsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

/**
 * The apps this device lets us hand to system Settings, from `PackageManager`
 * (`docs/screens/18-device-battery-and-apps.md` §6.1, §6.4; the competitor's `cd.j.b()`).
 *
 * The filter is the competitor's own, and it is worth stating plainly because the competitor's
 * **name** for the result is not: three flag masks — `FLAG_SYSTEM`, `FLAG_UPDATED_SYSTEM_APP` and
 * `FLAG_STOPPED` — over `getInstalledPackages`. So the list is *every installed third-party app that
 * is not currently stopped*. It is **not** a process list:
 * `ActivityManager.getRunningAppProcesses()` and `UsageStatsManager` are never called from that
 * cluster at all (`docs/reverse-engineering/18-device-battery-and-apps.md` §3.5).
 *
 * ### PENDING OWNER DECISION — §0.1 / `docs/system-architecture.md` §10.1 **P1**, unsettled
 *
 * [stoppableApps] is the honest port of what the data supports today. If the owner keeps
 * `PACKAGE_USAGE_STATS` and makes the claim real, **this one method** swaps to
 * `UsageStatsManager.queryEvents` over a recent window; no contract, ViewModel or screen changes, and
 * [usageAccess] — already read and already rendered — becomes a precondition instead of a rationale.
 * If the owner drops the screen, this file goes with the route. Nothing here forecloses either.
 *
 * > `QUERY_ALL_PACKAGES` is **not declared** anywhere in this project, so on API 30+ this
 * > enumeration returns only what package visibility already allows. That is a smaller list, not a
 * > wrong one, and the empty state says so rather than a permission being declared on a pending
 * > decision's behalf. Reported to the manifest's owner rather than added here.
 */
internal class PackageManagerRunningAppsRepository(
    context: Context,
    private val dispatchers: DispatcherProvider,
) : RunningAppsRepository {

    private val appContext = context.applicationContext
    private val packageManager: PackageManager get() = appContext.packageManager

    override suspend fun stoppableApps(): AppResult<ImmutableList<RunningApp>> =
        withContext(dispatchers.io) {
            try {
                installedPackages()
                    .mapNotNull { it.applicationInfo }
                    .filter { it.packageName != appContext.packageName && it.isStoppable() }
                    .map { RunningApp(packageName = it.packageName) }
                    .sortedBy { it.packageName }
                    .toImmutableList()
                    .asSuccess()
            } catch (e: RuntimeException) {
                // A TransactionTooLargeException on a device with several hundred packages arrives
                // here as a RuntimeException. The competitor does not catch it at all.
                AppError.Unexpected(e.message).asFailure()
            }
        }

    override suspend fun isStopped(packageName: String): AppResult<Boolean> =
        withContext(dispatchers.io) {
            try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                ((info.flags and ApplicationInfo.FLAG_STOPPED) != 0).asSuccess()
            } catch (_: PackageManager.NameNotFoundException) {
                // Uninstalled while we were away. Not an error the user needs told about, and not a
                // "stopped" claim either.
                AppError.NotFound(packageName).asFailure()
            }
        }

    /**
     * `AppOpsManager` is how a special access is checked; there is no `checkSelfPermission` answer
     * for `PACKAGE_USAGE_STATS`. `unsafeCheckOpNoThrow` arrived in API 29 and `minSdk` is 28, so the
     * older spelling is kept for exactly one API level.
     */
    @Suppress("DEPRECATION")
    override suspend fun usageAccess(): UsageAccessState = withContext(dispatchers.io) {
        val ops = appContext.getSystemService(AppOpsManager::class.java)
            ?: return@withContext UsageAccessState.Unknown
        val mode = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    appContext.packageName,
                )
            } else {
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    appContext.packageName,
                )
            }
        }.getOrNull() ?: return@withContext UsageAccessState.Unknown
        if (mode == AppOpsManager.MODE_ALLOWED) UsageAccessState.Granted else UsageAccessState.Denied
    }

    @Suppress("DEPRECATION")
    private fun installedPackages() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            packageManager.getInstalledPackages(0)
        }

    /** The competitor's three masks, named. A stopped app is already stopped; it is not offered. */
    private fun ApplicationInfo.isStoppable(): Boolean =
        (flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
            (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0 &&
            (flags and ApplicationInfo.FLAG_STOPPED) == 0
}
