package com.pion.phonecleaner.data.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.usecase.LoadInstalledAppsUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The one list-installed-apps implementation. **Four clusters read this port** — files, app-lock,
 * notification and device — and the competitor has three separate entry points to the same list
 * (`vd.c`, `vd.d.d(pm)`, `cd.j.b()`), which is how two of them came to disagree about which apps
 * belong in it (`docs/system-architecture.md` §4.1).
 *
 * **DECLARED IN `coreDataModule`, once.** The class is `internal` so that no cluster's own Koin
 * module *can* declare it — six independently written designs declared this type's siblings, and
 * Koin resolves a duplicate `single` by silently taking whichever module loaded last (`LLM.md` §6.4).
 *
 * Three rules it exists to hold:
 *
 *  * **No icon is ever enumerated.** `loadIcon` per package is what forced the competitor's
 *    hand-rolled 2-thread `ExecutorService` and its 50-entry `LruCache<Drawable>`; a row asks
 *    `AppIconLoader` by package name at draw time (`docs/screens/17` §303).
 *  * **`includeWithoutLauncher` is a real distinction, not a convenience.** The App Manager lists
 *    apps with no launcher activity and the Permission Manager does not. Collapsing the two
 *    enumerations without the parameter would silently give one caller the other's answer.
 *  * **The enumeration is cached in memory** and runs on `dispatchers.default`: it is CPU-bound
 *    string work over a few hundred `ApplicationInfo`s, and four clusters ask for it.
 */
internal class PackageManagerInstalledAppsRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val permissions: PermissionRepository,
) : InstalledAppsRepository {

    private val mutex = Mutex()
    private val cache = HashMap<Boolean, ImmutableList<InstalledApp>>()

    override suspend fun installedApps(
        includeWithoutLauncher: Boolean,
    ): AppResult<ImmutableList<InstalledApp>> = withContext(dispatchers.default) {
        try {
            mutex.withLock {
                cache.getOrPut(includeWithoutLauncher) { enumerate(includeWithoutLauncher) }
            }.asSuccess()
        } catch (e: SecurityException) {
            // Never an empty list to mean "refused": that is how `cd.d.e` reports a SecurityException,
            // and the user is then told nothing at all.
            AppError.PermissionDenied(e.message).asFailure()
        }
    }

    override suspend fun find(packageName: String): AppResult<InstalledApp?> =
        when (val all = installedApps(includeWithoutLauncher = true)) {
            is AppResult.Failure -> all
            is AppResult.Success -> all.value.firstOrNull { it.packageName == packageName }.asSuccess()
        }

    private fun enumerate(includeWithoutLauncher: Boolean): ImmutableList<InstalledApp> {
        val pm = context.packageManager
        val launchable = launchablePackages(pm)
        val lastUsed = lastUsedByPackage()
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filter { it.packageName != context.packageName }
            .filter { includeWithoutLauncher || it.packageName in launchable }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    uid = info.uid,
                    // `sourceDir` length is a SIZE. The competitor's row renders this same number
                    // through a `yyyy-MM-dd` formatter and calls it the installation time.
                    apkBytes = runCatching { File(info.sourceDir).length() }.getOrDefault(0L),
                    // appBytesOnDisk stays null here on purpose: measuring it costs a
                    // StorageStatsManager round trip per package that three of the four callers do
                    // not want, so it belongs to `AppStorageStatsRepository` (§4.1).
                    lastUsedAtMillis = lastUsed[info.packageName] ?: 0L,
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
            .toImmutableList()
    }

    private fun launchablePackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrDefault(emptySet())
    }

    /**
     * PENDING OWNER DECISION (3) — whether the app asks the user to grant `PACKAGE_USAGE_STATS` by
     * hand. Without that grant this map is empty and every app's `lastUsedAtMillis` is `0`, which
     * callers must read as *"not known"*, never as *"not used"*. Nothing here assumes an outcome: the
     * grant is checked, never requested, and its absence is not an error.
     */
    private fun lastUsedByPackage(): Map<String, Long> {
        if (!permissions.isGranted(AppPermission.UsageStats)) return emptyMap()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val from = now - LoadInstalledAppsUseCase.USAGE_WINDOW_DAYS * MILLIS_PER_DAY
        return runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, from, now)
                .groupingBy { it.packageName }
                .fold(0L) { acc, stats -> maxOf(acc, stats.lastTimeUsed) }
        }.getOrDefault(emptyMap())
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

        /**
         * UNKNOWN — whether system packages belong in the list. `docs/screens/14` §5 states only the
         * launcher-activity distinction, and no appendix mentions `FLAG_SYSTEM`. Filtering them would
         * be a rule no source states; they are therefore listed, and the platform refuses an
         * uninstall the user cannot perform anyway.
         */
        @Suppress("unused")
        val SYSTEM_FLAG = ApplicationInfo.FLAG_SYSTEM
    }
}
