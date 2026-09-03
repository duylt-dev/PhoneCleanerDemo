package com.pion.phonecleaner.data.app

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
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
 * Four rules it exists to hold:
 *
 *  * **No icon is ever enumerated.** `loadIcon` per package is what forced the competitor's
 *    hand-rolled 2-thread `ExecutorService` and its 50-entry `LruCache<Drawable>`; a row asks
 *    `AppIconLoader` by package name at draw time (`docs/screens/17` §303).
 *  * **`includeWithoutLauncher` is a real distinction, not a convenience.** The App Manager lists
 *    apps with no launcher activity and the Permission Manager does not. Collapsing the two
 *    enumerations without the parameter would silently give one caller the other's answer.
 *  * **System packages are enumerated, not dropped.** Owner decision (2026-09-03) hides them from
 *    the App Manager, and `LoadInstalledAppsUseCase` is where that happens — three other clusters
 *    read this port and still want the whole list. This pass only *marks* them, via
 *    `InstalledApp.isSystem`.
 *  * **The enumeration is cached in memory** and runs on `dispatchers.default`: it is CPU-bound
 *    string work over a few hundred `ApplicationInfo`s, and four clusters ask for it.
 */
internal class PackageManagerInstalledAppsRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val permissions: PermissionRepository,
) : InstalledAppsRepository {

    private val mutex = Mutex()
    private val cache = HashMap<CacheKey, ImmutableList<InstalledApp>>()

    override suspend fun installedApps(
        includeWithoutLauncher: Boolean,
    ): AppResult<ImmutableList<InstalledApp>> = withContext(dispatchers.default) {
        try {
            // The grant is PART OF THE KEY, not a flag read once. It is given by hand on a Settings
            // page the user is handed out to, and the screen that sent them there re-enumerates on
            // its way back. Keying only on `includeWithoutLauncher` served that return trip the list
            // built while the grant was still denied, so every row read `lastUsedAtMillis = 0` and
            // App Manager showed "no usage data" for the rest of the process lifetime — the grant
            // appeared to do nothing.
            val key = CacheKey(includeWithoutLauncher, permissions.isGranted(AppPermission.UsageStats))
            mutex.withLock {
                cache.getOrPut(key) { enumerate(key) }
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

    /**
     * ONE `PackageManager` pass, over `PackageInfo` rather than `ApplicationInfo`.
     *
     * `getInstalledApplications` cannot answer *when was this installed*: `firstInstallTime` is on
     * `PackageInfo`, and reading it per package afterwards would be a second binder round trip for
     * every one of a few hundred apps. `getInstalledPackages` carries both, and `applicationInfo` is
     * the same object the old call returned — the filters, the label and the `sourceDir` length below
     * are unchanged.
     *
     * The flags go to `0`, from the `GET_META_DATA` the `ApplicationInfo` call passed. Nothing here
     * has ever read `metaData`, and its `Bundle` is the bulk of what an enumeration of a few hundred
     * packages pushes across the binder — a buffer with a ~1 MB ceiling that a `PackageInfo` list
     * fills faster than an `ApplicationInfo` one. Dropping an unread flag pays for the wider row.
     */
    private fun enumerate(key: CacheKey): ImmutableList<InstalledApp> {
        val pm = context.packageManager
        val launchable = launchablePackages(pm)
        val lastUsed = if (key.usageAccessGranted) lastUsedByPackage() else emptyMap()
        return pm.getInstalledPackages(0)
            .asSequence()
            .mapNotNull { pkg -> pkg.applicationInfo?.let { pkg to it } }
            .filter { (_, info) -> info.packageName != context.packageName }
            .filter { (_, info) -> key.includeWithoutLauncher || info.packageName in launchable }
            .map { (pkg, info) ->
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
                    firstInstallAtMillis = pkg.installTimeOrZero(),
                    isSystem = info.isSystemPackage(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
            .toImmutableList()
    }

    /**
     * `FLAG_UPDATED_SYSTEM_APP` counts as system, not as a user install.
     *
     * A preinstalled browser that the store has since updated keeps `FLAG_SYSTEM` and gains this
     * second flag; reading `FLAG_SYSTEM` alone would still call it a system app, but a device whose
     * vendor clears `FLAG_SYSTEM` on update would not, and the App Manager would then list a row
     * whose uninstall the platform refuses. Testing both makes the answer the same on every device.
     */
    private fun ApplicationInfo.isSystemPackage(): Boolean =
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    /**
     * `firstInstallTime` is *first* install on purpose: `lastUpdateTime` moves every time the store
     * pushes an update, so a row labelled "installed" would change date under a user who installed
     * the app two years ago and never touched it since.
     */
    private fun PackageInfo.installTimeOrZero(): Long = firstInstallTime.takeIf { it > 0L } ?: 0L

    private fun launchablePackages(pm: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0).mapTo(HashSet()) { it.activityInfo.packageName }
        }.getOrDefault(emptySet())
    }

    /**
     * `PACKAGE_USAGE_STATS` is declared (`:data`'s manifest) so this column can exist at all, and the
     * user grants it by hand from App Manager's rationale card. Without that grant [enumerate] does
     * not call this at all and every app's `lastUsedAtMillis` is `0`, which callers must read as
     * *"not known"*, never as *"not used"*. The grant is checked in [installedApps], where it is also
     * part of the cache key; it is never requested, and its absence is not an error.
     */
    private fun lastUsedByPackage(): Map<String, Long> {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
        val now = System.currentTimeMillis()
        val from = now - LoadInstalledAppsUseCase.USAGE_WINDOW_DAYS * MILLIS_PER_DAY
        return runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, from, now)
                .groupingBy { it.packageName }
                .fold(0L) { acc, stats -> maxOf(acc, stats.lastTimeUsed) }
        }.getOrDefault(emptyMap())
    }

    /** Two enumerations that differ in either field are two different lists, not one stale one. */
    private data class CacheKey(
        val includeWithoutLauncher: Boolean,
        val usageAccessGranted: Boolean,
    )

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
