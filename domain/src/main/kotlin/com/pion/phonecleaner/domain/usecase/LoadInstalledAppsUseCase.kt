package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.app.InstalledAppsProgress
import com.pion.phonecleaner.domain.model.app.ManagedApp
import com.pion.phonecleaner.domain.repository.AppStorageStatsRepository
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * The App Manager's two-stage load (`docs/screens/14-file-tools-and-app-manager.md` §5.2).
 *
 * `Enumerated` first — labels and APK lengths from one `PackageManager` pass — then one `Sized` per
 * app as each `StorageStatsManager` measurement lands. **The competitor blocks the whole screen
 * behind `awaitAll()` and then pads the wait to 4 000 ms**, so the slowest part of the work is also
 * the part that gates the first frame, for no reason the user can observe.
 *
 * `includeWithoutLauncher = true` is deliberate and is the distinction the shared port keeps: the App
 * Manager lists apps with no launcher activity and the Permission Manager does not — two different
 * enumerations in the competitor (`vd.c.a` and `vd.d.d`), one port with one parameter here.
 *
 * A failure to enumerate is returned as a failure. There is no partial-success arm because there is
 * no partial answer: either the package list was readable or it was not.
 */
class LoadInstalledAppsUseCase(
    private val installedApps: InstalledAppsRepository,
    private val stats: AppStorageStatsRepository,
) {

    operator fun invoke(): Flow<InstalledAppsProgress> = flow {
        val apps = when (val result = installedApps.installedApps(includeWithoutLauncher = true)) {
            // An exception never crosses a layer boundary (MVI §5); a refusal is an arm.
            is AppResult.Failure -> {
                emit(InstalledAppsProgress.Failed(result.error))
                return@flow
            }

            is AppResult.Success -> result.value
        }
        emit(
            InstalledAppsProgress.Enumerated(
                apps.map { app ->
                    ManagedApp(
                        packageName = app.packageName,
                        label = app.label,
                        uid = app.uid,
                        apkBytes = app.apkBytes,
                        // UNKNOWN — `InstalledApp` carries no install timestamp, and it is the
                        // shared port's model, which this cluster may not extend. `PackageInfo
                        // .firstInstallTime` is read by the implementation of that port or not at
                        // all; until it is, the row renders "install date unknown" rather than a
                        // fabricated one. Looked for: an install-time field on `InstalledApp` in the
                        // shared API digest, and a second accessor on `InstalledAppsRepository`.
                        firstInstallEpochMillis = 0L,
                        lastUsedEpochMillis = app.lastUsedAtMillis,
                    )
                }.toImmutableList(),
            ),
        )
        stats.statsFor(apps).collect { emit(InstalledAppsProgress.Sized(it)) }
    }

    companion object {
        /**
         * How far back the "last used" query looks.
         *
         * UNKNOWN — no source states a window. What §5.5 states is the defect: the competitor queries
         * **720 days** while its own copy says *"Not Used For One Year"*, so the number in the string
         * and the number in the query are two numbers maintained by hand. There is one number here,
         * and every piece of copy that mentions a period is formatted from it.
         */
        const val USAGE_WINDOW_DAYS: Long = 30L
    }
}
