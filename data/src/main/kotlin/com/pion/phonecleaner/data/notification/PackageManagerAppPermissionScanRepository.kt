package com.pion.phonecleaner.data.notification

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.catalog.SensitivePermissionCatalog
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.model.permission.GrantedPermission
import com.pion.phonecleaner.domain.repository.AppPermissionScanRepository
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

/**
 * Port 2 of the three — "what permissions do **other** apps hold?" — replacing `vd.d.b`/`.c`/`.f`
 * (`docs/screens/17-notification-and-permissions.md` §0.2, `docs/system-architecture.md` §4.4).
 *
 * DECLARED IN `notificationDataModule`, not `coreDataModule`: the interface is in `:domain` because
 * §4.4 defines it as one of the three ports the permission layer collapses to, but the implementation
 * belongs to this cluster.
 *
 * It composes [InstalledAppsRepository] with `includeWithoutLauncher = false`. That parameter is not
 * decoration: `vd.c.a()` and `vd.d.d(pm)` are **different enumerations** — the App Manager includes
 * apps with no launcher activity, the Permission Manager does not — and collapsing them would silently
 * pick one caller's answer for both.
 *
 * ### The three competitor defects it closes, all in `vd.d`
 *
 * 1. **Nothing is discarded.** A permission that is neither in the sensitive table nor
 *    `PROTECTION_NORMAL` goes to [AppPermissionReport.other]; the competitor drops it from both
 *    buckets, so its two counts do not add up to what the app actually holds (§4.5).
 * 2. **A label is never a reason to drop a row.** `getString(info.labelRes)` throws when `labelRes` is
 *    0, and the competitor's per-permission `catch` then drops the permission silently, so its counts
 *    drift with no signal. Here the fall-back is the raw permission name.
 * 3. **It runs on `dispatchers.default`.** The walk is cancellable by the collecting job, which the
 *    competitor's unguarded `launch(IO)` is not.
 */
internal class PackageManagerAppPermissionScanRepository(
    context: Context,
    private val installedApps: InstalledAppsRepository,
    private val dispatchers: DispatcherProvider,
) : AppPermissionScanRepository {

    private val packageManager: PackageManager = context.packageManager

    /** Protection levels resolve to the same answer for every app, so they are resolved once per scan. */
    private val protectionCache = mutableMapOf<String, Int?>()

    override suspend fun scanAll(): AppResult<ImmutableList<AppPermissionReport>> =
        withContext(dispatchers.default) {
            when (val installed = installedApps.installedApps(includeWithoutLauncher = false)) {
                is AppResult.Failure -> installed
                is AppResult.Success -> AppResult.Success(
                    installed.value
                        .mapNotNull { app -> report(app.packageName, app.label) }
                        .sortedWith(compareByDescending<AppPermissionReport> { it.sensitiveCount }
                            .thenBy { it.label.lowercase() })
                        .toImmutableList(),
                )
            }
        }

    override suspend fun refresh(packageName: String): AppResult<AppPermissionReport?> =
        withContext(dispatchers.default) {
            when (val found = installedApps.find(packageName)) {
                is AppResult.Failure -> found
                is AppResult.Success -> {
                    val app = found.value
                    AppResult.Success(app?.let { report(it.packageName, it.label) })
                }
            }
        }

    /**
     * `null` means the package is gone, or holds nothing granted at all — the caller **removes** the
     * row rather than leaving a stale one.
     */
    private fun report(packageName: String, label: String): AppPermissionReport? {
        val info = packageInfo(packageName) ?: return null
        val requested = info.requestedPermissions ?: return null
        val flags = info.requestedPermissionsFlags

        val sensitive = mutableListOf<GrantedPermission>()
        val normal = mutableListOf<GrantedPermission>()
        val other = mutableListOf<GrantedPermission>()

        requested.forEachIndexed { index, name ->
            if (!isGranted(flags, index)) return@forEachIndexed
            val permission = GrantedPermission(name, label(name), description(name))
            when {
                name in SensitivePermissionCatalog.allSensitive -> sensitive += permission
                protectionOf(name) == PermissionInfo.PROTECTION_NORMAL -> normal += permission
                else -> other += permission
            }
        }
        if (sensitive.isEmpty() && normal.isEmpty() && other.isEmpty()) return null
        return AppPermissionReport(
            packageName = packageName,
            label = label,
            sensitive = sensitive.toImmutableList(),
            normal = normal.toImmutableList(),
            other = other.toImmutableList(),
        )
    }

    /**
     * `requestedPermissionsFlags` and not `checkPermission`: the flag array is already in the row this
     * query returned, and asking the package manager a second question per permission is N extra
     * binder round trips for an answer we were handed.
     */
    private fun isGranted(flags: IntArray?, index: Int): Boolean {
        val bits = flags?.getOrNull(index) ?: return false
        return bits and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
    }

    private fun packageInfo(packageName: String): PackageInfo? = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
    } catch (missing: PackageManager.NameNotFoundException) {
        null
    }

    /** Both label lookups fall back to null, and the row renders the raw name. Never dropped. */
    private fun label(name: String): String? =
        runCatching { permissionInfo(name)?.loadLabel(packageManager)?.toString() }.getOrNull()

    private fun description(name: String): String? =
        runCatching { permissionInfo(name)?.loadDescription(packageManager)?.toString() }.getOrNull()

    private fun protectionOf(name: String): Int? = protectionCache.getOrPut(name) {
        @Suppress("DEPRECATION")
        permissionInfo(name)?.protectionLevel?.and(PermissionInfo.PROTECTION_MASK_BASE)
    }

    private fun permissionInfo(name: String): PermissionInfo? = try {
        packageManager.getPermissionInfo(name, 0)
    } catch (missing: PackageManager.NameNotFoundException) {
        null
    }
}

