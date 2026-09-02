package com.pion.phonecleaner.feature.network.testing

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.network.SpeedTestProgress
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.domain.model.network.TrafficReport
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.repository.NetworkTrafficRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.SpeedTestRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

/**
 * Hand-written fakes, no mocking library (`LLM.md` §9). Each has a switchable failure mode, because
 * the categories that catch real defects are crash containment and stuck states, not reducers.
 */

internal class FakePermissionRepository : PermissionRepository {
    val granted = MutableStateFlow<ImmutableSet<AppPermission>>(persistentSetOf())
    override fun observe(): Flow<ImmutableSet<AppPermission>> = granted
    override fun isGranted(permission: AppPermission): Boolean = permission in granted.value
    override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> = persistentSetOf()
}

internal class FakeFeatureUsageRepository : FeatureUsageRepository {
    val marked = mutableListOf<FeatureId>()
    override suspend fun markUsed(feature: FeatureId) {
        marked += feature
    }

    override fun lastUsed(feature: FeatureId): Flow<Instant?> = MutableStateFlow(null)
    override fun staleFeatures(): Flow<ImmutableList<FeatureId>> = MutableStateFlow(persistentListOf())
    override suspend fun recommend(): FeatureId = FeatureId.NetworkTraffic
}

internal class FakeInstalledAppsRepository(
    var apps: ImmutableList<InstalledApp> = persistentListOf(),
    var failure: AppResult.Failure? = null,
) : InstalledAppsRepository {
    override suspend fun installedApps(
        includeWithoutLauncher: Boolean,
    ): AppResult<ImmutableList<InstalledApp>> = failure ?: AppResult.Success(apps)

    override suspend fun find(packageName: String): AppResult<InstalledApp?> =
        AppResult.Success(apps.firstOrNull { it.packageName == packageName })
}

/**
 * [gate] holds a query open, which is the only way to observe the screen **while** it is loading —
 * the state a cancel-and-replace has to be correct about. [throwOnReport] covers crash containment,
 * and [answers] queued per period covers the stale-result guard.
 */
internal class FakeNetworkTrafficRepository(
    var answer: AppResult<TrafficReport>? = null,
    var throwOnReport: Boolean = false,
    var gate: CompletableDeferred<Unit>? = null,
) : NetworkTrafficRepository {
    val requested = mutableListOf<TrafficPeriod>()
    val answers = mutableMapOf<TrafficPeriod, AppResult<TrafficReport>>()

    /** Set to have the call never return, so the timeout path can be observed. */
    var neverReturns: Boolean = false

    override suspend fun report(period: TrafficPeriod): AppResult<TrafficReport> {
        requested += period
        gate?.await()
        if (throwOnReport) error("traffic engine blew up")
        if (neverReturns) CompletableDeferred<Unit>().await()
        return answers[period] ?: answer ?: AppResult.Success(emptyReport(period))
    }
}

internal fun emptyReport(period: TrafficPeriod) = TrafficReport(
    period = period,
    mobileBytes = 0L,
    wifiBytes = 0L,
    apps = persistentListOf(),
)

internal class FakeSpeedTestRepository(
    var emissions: List<SpeedTestProgress> = listOf(SpeedTestProgress.NotConfigured),
    var throwOnMeasure: Boolean = false,
    var neverCompletes: Boolean = false,
) : SpeedTestRepository {
    var calls = 0
        private set

    override fun measure(): Flow<SpeedTestProgress> = flow {
        calls++
        if (throwOnMeasure) error("speed test blew up")
        emissions.forEach { emit(it) }
        if (neverCompletes) CompletableDeferred<Unit>().await()
    }
}
