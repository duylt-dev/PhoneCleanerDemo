package com.pion.phonecleaner.feature.home

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.device.StorageInfo
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.JunkRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.StorageInfoRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Hand-written fakes, no mocking library (`LLM.md` §9). A fake you can read beats a mock you have to
 * decode, and every one of these is small enough to read in one screen.
 */
internal class FakeStorageInfoRepository : StorageInfoRepository {
    val info = MutableStateFlow(StorageInfo(totalBytes = 0L, availableBytes = 0L))
    override fun observe(): Flow<StorageInfo> = info
    override suspend fun current(): AppResult<StorageInfo> = AppResult.Success(info.value)
}

internal class FakeCleanupLedger : CleanupLedger {
    val lifetime = MutableStateFlow(0L)
    override suspend fun record(freedBytes: Long): AppResult<Unit> {
        lifetime.value += freedBytes
        return AppResult.Success(Unit)
    }

    override fun observeLifetimeFreedBytes(): Flow<Long> = lifetime
}

/**
 * [missing] is a reassignable lambda so one test can hold a feature behind a permission, let the user
 * grant it, and re-enter the same reducer — which is the whole shape of the permission funnel.
 */
internal class FakePermissionRepository(
    var missing: (FeatureId) -> ImmutableSet<AppPermission> = { persistentSetOf() },
) : PermissionRepository {
    val granted = MutableStateFlow<ImmutableSet<AppPermission>>(persistentSetOf())
    override fun observe(): Flow<ImmutableSet<AppPermission>> = granted
    override fun isGranted(permission: AppPermission): Boolean = permission in granted.value
    override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> = missing(feature)
}

@OptIn(ExperimentalTime::class)
internal class FakeFeatureUsageRepository(
    private val recommendation: FeatureId = FeatureId.BigFiles,
) : FeatureUsageRepository {
    val marked = mutableListOf<FeatureId>()
    override suspend fun markUsed(feature: FeatureId) { marked += feature }
    override fun lastUsed(feature: FeatureId): Flow<Instant?> = MutableStateFlow(null)
    override fun staleFeatures(): Flow<ImmutableList<FeatureId>> = MutableStateFlow(persistentListOf())
    override suspend fun recommend(): FeatureId = recommendation
}

/**
 * [refreshes] is what the single-flight test reads: a second `ScreenResumed` while the first is
 * still running must not reach the repository, and the one after it must.
 */
internal class FakeJunkRepository(
    private val outcome: () -> AppResult<Long> = { AppResult.Success(0L) },
) : JunkRepository {
    val cached = MutableStateFlow<Long?>(null)
    var refreshes = 0
        private set

    override fun scan(): Flow<ScanProgress> = emptyFlow()
    override fun clean(paths: Set<String>): Flow<CleanProgress> = emptyFlow()
    override fun cachedJunkBytes(): Flow<Long?> = cached
    override suspend fun refreshEstimate(force: Boolean): AppResult<Long> {
        refreshes++
        return outcome()
    }

    override suspend fun invalidateEstimate() { cached.value = null }
}

internal class RecordingAnalyticsRepository : AnalyticsRepository {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) { events += event }

    val featureOpens: List<FeatureId>
        get() = events.filterIsInstance<AnalyticsEvent.FeatureOpened>().map { it.feature }
}
