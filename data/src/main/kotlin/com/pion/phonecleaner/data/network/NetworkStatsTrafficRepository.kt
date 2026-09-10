package com.pion.phonecleaner.data.network

import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.RemoteException
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.domain.model.network.AppTraffic
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.domain.model.network.TrafficReport
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.NetworkTrafficRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

/**
 * `NetworkTrafficRepository` over `NetworkStatsManager` — the real, measured half of this cluster
 * (`docs/screens/19-network-and-speed-test.md` §1). Named for its mechanism, per `LLM.md` §5: this
 * is the only public API that can attribute bytes per app, and if that ever changes the name says
 * which class has to be replaced.
 *
 * Three things it does that `cd.d` does not:
 *
 * 1. **A failure is a failure.** A missing usage-access grant is `AppError.PermissionDenied`, a
 *    device with no `netstats` service is `AppError.NotFound`; neither is an empty list.
 * 2. **A shared UID stays one row.** All of its packages ride on [AppTraffic.packageNames] instead of
 *    the first one being credited with the lot (`docs/reverse-engineering/19-network-and-speed-test.md` :502).
 * 3. **The totals are the sums of the rows returned**, so the header and the list cannot disagree (:506).
 *
 * **No system-app filter.** The competitor drops `FLAG_SYSTEM` packages silently and then reports
 * totals that exclude them. `docs/screens/19-network-and-speed-test.md` §5 open item 4 proposes making
 * that a *visible* filter and records that no report specifies its copy, so nothing is dropped here
 * and no copy is invented. A UID with no resolvable package is the one row that does not survive —
 * rendering a raw package id to a user is not a fallback, it is a leak of an internal name.
 *
 * PENDING OWNER DECISION 3 — whether *this* screen asks for `PACKAGE_USAGE_STATS`. The manifest now
 * declares the permission (for App Manager's *Last used* column), so the grant is reachable; nothing
 * here asks for it. Until the user gives it this repository answers `PermissionDenied` and the screen
 * renders its ungranted state, which is still the honest outcome of an undecided question.
 */
internal class NetworkStatsTrafficRepository(
    context: Context,
    private val dispatchers: DispatcherProvider,
    clock: AppClock,
    private val log: AppLogger,
) : NetworkTrafficRepository {

    private val packageManager: PackageManager = context.packageManager
    private val statsManager: NetworkStatsManager? =
        context.getSystemService(NetworkStatsManager::class.java)
    private val window = TrafficWindow(clock)

    override suspend fun report(period: TrafficPeriod): AppResult<TrafficReport> =
        withContext(dispatchers.io) {
            val manager = statsManager
                ?: return@withContext AppResult.Failure(AppError.NotFound(NETSTATS_SERVICE))
            val range = window.of(period)
            try {
                val mobile = NetworkStatsQuery.bytesByUid(
                    manager,
                    MOBILE,
                    range.startMillis,
                    range.endMillis,
                )
                val wifi = NetworkStatsQuery.bytesByUid(
                    manager,
                    WIFI,
                    range.startMillis,
                    range.endMillis,
                )
                AppResult.Success(reportOf(period, mobile, wifi))
            } catch (denied: SecurityException) {
                log.e(denied) { "Usage access refused the $period traffic query" }
                AppResult.Failure(AppError.PermissionDenied(AppPermission.UsageStats.name))
            } catch (unreachable: RemoteException) {
                log.e(unreachable) { "system_server refused the $period traffic query" }
                AppResult.Failure(AppError.Unexpected(unreachable.message))
            }
        }

    private fun reportOf(
        period: TrafficPeriod,
        mobile: Map<Int, Long>,
        wifi: Map<Int, Long>,
    ): TrafficReport {
        val apps = (mobile.keys + wifi.keys).mapNotNull { uid ->
            val packages = packagesFor(uid) ?: return@mapNotNull null
            AppTraffic(
                uid = uid,
                packageNames = packages,
                mobileBytes = mobile[uid] ?: 0L,
                wifiBytes = wifi[uid] ?: 0L,
            )
        }.sortedByDescending(AppTraffic::totalBytes)

        return TrafficReport(
            period = period,
            mobileBytes = apps.sumOf(AppTraffic::mobileBytes),
            wifiBytes = apps.sumOf(AppTraffic::wifiBytes),
            apps = apps.toImmutableList(),
        )
    }

    /**
     * `null` when the UID resolves to nothing this app may see. `NetworkStats` history outlives an
     * install, and package visibility filtering hides packages we hold no `<queries>` entry for, so a
     * UID with no name is a normal outcome rather than an error.
     */
    private fun packagesFor(uid: Int): ImmutableList<String>? {
        val names = packageManager.getPackagesForUid(uid)?.filter(String::isNotBlank).orEmpty()
        return if (names.isEmpty()) null else names.toImmutableList()
    }

    private companion object {
        const val NETSTATS_SERVICE = "NetworkStatsManager"

        /**
         * `ConnectivityManager.TYPE_MOBILE` / `TYPE_WIFI`. Deprecated as *connectivity* constants,
         * but they remain the argument `NetworkStatsManager.querySummary(int, …)` takes — the
         * `NetworkTemplate` overloads that replace them are `@SystemApi`.
         */
        @Suppress("DEPRECATION")
        const val MOBILE = ConnectivityManager.TYPE_MOBILE

        @Suppress("DEPRECATION")
        const val WIFI = ConnectivityManager.TYPE_WIFI
    }
}
