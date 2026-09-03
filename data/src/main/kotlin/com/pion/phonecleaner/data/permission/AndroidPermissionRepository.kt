package com.pion.phonecleaner.data.permission

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.pion.phonecleaner.data.lifecycle.AppLifecycleObserver
import com.pion.phonecleaner.domain.catalog.FeatureCatalog
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.PermissionRepository
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Port 1 of the three the permission layer splits into (`docs/system-architecture.md` §4.4):
 * **"does THIS app hold permission P?"** — and nothing else. Port 2 is
 * [com.pion.phonecleaner.domain.repository.AppPermissionScanRepository]; port 3 is not a repository
 * at all, it is [SpecialAccessIntents].
 *
 * It replaces `od.z` — ~870 lines and 14 predicates — plus the XXPermissions facade `o7.m0`. **One
 * `single`, in `coreDataModule`.** Three cluster designs each declared their own under the names
 * `UsageAccessRepository`, `NotificationAccessRepository`, `AppLockPermissionRepository` and
 * `SpecialAccessRepository`; Koin overrides silently, so those would have been one runtime coin-flip
 * rather than four ports (`docs/system-architecture.md` §5.1, `LLM.md` §6.4).
 *
 * ### No permission library
 *
 * XXPermissions is out of scope, and the replacement is deliberately smaller: `checkSelfPermission`
 * for runtime permissions, an app-op check for special access, an explicit `Settings.ACTION_*` intent
 * per special access ([SpecialAccessIntents]) and `ActivityResultContracts` at the Route. A library
 * whose whole job is to hide `SDK_INT` branches is a dependency in exchange for the branches below.
 *
 * ### Why nothing here is `suspend`, and why there is no `flowOn`
 *
 * The port is non-suspend by design: [isGranted] and [missingFor] are read **inside a reducer**, and
 * a reducer neither suspends nor waits. Every check is either a local package-manager lookup or one
 * cheap `system_server` round trip, and [observe] re-evaluates all eleven only when the process comes
 * back to the foreground. Putting [observe] on `dispatchers.io` while [isGranted] stays on the
 * caller's thread would make one repository answer the same question from two threads.
 */
internal class AndroidPermissionRepository(
    private val context: Context,
    private val appLifecycle: AppLifecycleObserver,
) : PermissionRepository {

    /** The branch-sensitive half — see [StorageAccessChecks] for why it is a separate file. */
    private val storage = StorageAccessChecks(context)

    /**
     * Re-emits when the process returns to the foreground, which is what makes the permission funnel
     * work (`LLM.md` §7.4): the user leaves for Settings, grants, presses Back, the process is
     * started again, this flow re-emits, and the same reducer is re-entered. The competitor's
     * Permission Centre checks once in `z()` and overrides no `onResume`, so granting and returning
     * leaves the stale card on screen — while another of its screens *does* re-check, so the app
     * contradicts itself.
     *
     * `AppLifecycleObserver.isInForeground` is a `StateFlow`, so a collector gets the current answer
     * immediately and then one emission per transition; `distinctUntilChanged` means a foreground
     * round trip that changed nothing costs no emission and no recomposition.
     *
     * A grant made **inside** the app (a runtime dialog) produces no lifecycle transition. That path
     * is the screen's `ScreenResumed` intent re-reading [isGranted], per the funnel — not a second
     * source of truth here.
     */
    override fun observe(): Flow<ImmutableSet<AppPermission>> =
        appLifecycle.isInForeground
            .map { granted() }
            .distinctUntilChanged()

    override fun isGranted(permission: AppPermission): Boolean = when (permission) {
        AppPermission.Storage -> storage.legacyStorage()
        AppPermission.AllFiles -> storage.allFiles()
        AppPermission.Media -> storage.media()
        AppPermission.WhatsAppFolder -> storage.whatsAppTree()
        AppPermission.Notifications -> NotificationManagerCompat.from(context).areNotificationsEnabled()
        AppPermission.NotificationListener -> listenerEnabled()
        AppPermission.UsageStats -> appOpAllowed(AppOpsManager.OPSTR_GET_USAGE_STATS)
        AppPermission.Overlay -> Settings.canDrawOverlays(context)
        AppPermission.WriteSettings -> Settings.System.canWrite(context)
        AppPermission.DoNotDisturb -> notificationPolicyGranted()
        AppPermission.IgnoreBatteryOptimizations -> batteryOptimizationsIgnored()
    }

    /**
     * What [feature] still needs, read off [FeatureCatalog] — the catalogue is the gate, and the gate
     * is in the reducer. The competitor's gate is 14 lambdas inside its router (`md.g1.g0`), so a
     * destination and its precondition are edited in different files.
     *
     * An empty result means the reducer may raise its navigation Effect. `FeatureCatalog.requires`
     * states only the rows the corpus actually settles, so an unlisted feature is ungated here and
     * fails visibly at the repository with `AppError.PermissionDenied` — never silently.
     */
    override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> =
        FeatureCatalog.requires(feature)
            .filterNotTo(LinkedHashSet(), ::isGranted)
            .toImmutableSet()

    private fun granted(): ImmutableSet<AppPermission> =
        AppPermission.entries.filterTo(LinkedHashSet(), ::isGranted).toImmutableSet()

    /**
     * `NotificationManagerCompat.getEnabledListenerPackages` and not a hand-parsed
     * `Settings.Secure.getString("enabled_notification_listeners")`: the setting is a
     * colon-separated list of flattened component names, and every hand-rolled parser of it — the
     * competitor's `od.z.i0()` included — reads a package whose name is a prefix of ours as a match.
     */
    private fun listenerEnabled(): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    private fun notificationPolicyGranted(): Boolean =
        context.getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true

    private fun batteryOptimizationsIgnored(): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * Special access granted through Settings is an **app-op**, not a runtime permission, so
     * `checkSelfPermission` cannot see it.
     *
     * `PACKAGE_USAGE_STATS` **is** declared (`:data`'s manifest, with the reason in place). It has to
     * be: the system's Usage Access page lists only apps that declare it, so before the declaration
     * the grant was unreachable and this predicate read `false` on every device. Declared is not
     * granted — the user gives this one by hand — and a feature that needs it still degrades
     * **visibly** while they have not. The declaration serves App Manager's *Last used* column
     * (`docs/screens/14` §5); whether the running-apps screen gates on the same grant is still an
     * open owner decision (`docs/system-architecture.md` §10.1 P1).
     */
    private fun appOpAllowed(op: String): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(op, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(op, Process.myUid(), context.packageName)
            }
        } catch (denied: SecurityException) {
            // The op is not ours to read on this device. Absent, not granted — and never a crash in
            // a predicate a reducer calls.
            return false
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
