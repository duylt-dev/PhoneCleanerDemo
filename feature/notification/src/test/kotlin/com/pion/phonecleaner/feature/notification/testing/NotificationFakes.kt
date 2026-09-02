package com.pion.phonecleaner.feature.notification.testing

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.notification.HiddenNotification
import com.pion.phonecleaner.domain.model.notification.NotificationHidingApp
import com.pion.phonecleaner.domain.model.notification.NotificationHidingSettings
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.permission.AppPermissionReport
import com.pion.phonecleaner.domain.model.permission.GrantedPermission
import com.pion.phonecleaner.domain.repository.AppPermissionScanRepository
import com.pion.phonecleaner.domain.repository.FeatureStatsRepository
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.HiddenNotificationNotifier
import com.pion.phonecleaner.domain.repository.NotificationCleanerRepository
import com.pion.phonecleaner.domain.repository.NotificationHidingSettingsStore
import com.pion.phonecleaner.domain.repository.PermissionRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

/**
 * Hand-written fakes, no mocking library (`LLM.md` §9). A fake you can read beats a mock you have to
 * decode, and every one of these is small enough to read in one screen.
 */
internal class FakeNotificationHidingSettingsStore(
    initial: NotificationHidingSettings = settings(),
) : NotificationHidingSettingsStore {

    val state = MutableStateFlow(initial)

    /** Set to fail the next write; the ViewModel must then leave the switch where the store had it. */
    var writeError: AppError? = null

    /** Set to make the upstream throw, which is the only way this screen reaches an error at all. */
    var observeError: Throwable? = null

    val masterWrites = mutableListOf<Boolean>()
    val appWrites = mutableListOf<Pair<String, Boolean>>()

    override fun observe(): Flow<NotificationHidingSettings> = flow {
        observeError?.let { throw it }
        state.collect { emit(it) }
    }

    override suspend fun setMasterEnabled(enabled: Boolean): AppResult<Unit> {
        masterWrites += enabled
        writeError?.let { return AppResult.Failure(it) }
        state.value = state.value.copy(isMasterEnabled = enabled)
        return AppResult.Success(Unit)
    }

    override suspend fun setAppEnabled(packageName: String, enabled: Boolean): AppResult<Unit> {
        appWrites += packageName to enabled
        writeError?.let { return AppResult.Failure(it) }
        state.value = state.value.copy(
            apps = state.value.apps
                .map { if (it.packageName == packageName) it.copy(isHidingEnabled = enabled) else it }
                .toImmutableList(),
        )
        return AppResult.Success(Unit)
    }
}

internal class FakeNotificationCleanerRepository(
    initial: List<HiddenNotification> = emptyList(),
) : NotificationCleanerRepository {

    val rows = MutableStateFlow(initial.toImmutableList())
    var clearError: AppError? = null
    var dismissError: AppError? = null
    val dismissed = mutableListOf<String>()
    var clearCalls = 0
        private set

    override fun observeHidden(): Flow<ImmutableList<HiddenNotification>> = rows

    override fun observeHiddenCount(): Flow<Int> = rows.map { it.size }

    override suspend fun insert(notification: HiddenNotification): AppResult<Unit> {
        rows.value = (rows.value + notification).toImmutableList()
        return AppResult.Success(Unit)
    }

    override suspend fun dismiss(key: String): AppResult<Unit> {
        dismissed += key
        dismissError?.let { return AppResult.Failure(it) }
        rows.value = rows.value.filterNot { it.key == key }.toImmutableList()
        return AppResult.Success(Unit)
    }

    override suspend fun clearAll(): AppResult<Int> {
        clearCalls++
        clearError?.let { return AppResult.Failure(it) }
        val removed = rows.value.size
        rows.value = persistentListOf()
        return AppResult.Success(removed)
    }
}

internal class FakeHiddenNotificationNotifier : HiddenNotificationNotifier {
    var dismissals = 0
        private set

    override suspend fun advertise(count: Int, packageNames: List<String>) = AppResult.Success(Unit)

    override fun dismissSummary() {
        dismissals++
    }
}

internal class FakePermissionRepository(
    granted: Set<AppPermission> = emptySet(),
) : PermissionRepository {

    var granted: Set<AppPermission> = granted

    override fun observe(): Flow<ImmutableSet<AppPermission>> =
        MutableStateFlow(granted.toImmutableSet())

    override fun isGranted(permission: AppPermission): Boolean = permission in granted

    override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> = persistentSetOf()
}

internal class FakeFeatureUsageRepository : FeatureUsageRepository {
    val marked = mutableListOf<FeatureId>()

    override suspend fun markUsed(feature: FeatureId) {
        marked += feature
    }

    override fun lastUsed(feature: FeatureId): Flow<Instant?> = MutableStateFlow(null)

    override fun staleFeatures(): Flow<ImmutableList<FeatureId>> = MutableStateFlow(persistentListOf())

    override suspend fun recommend(): FeatureId = FeatureId.JunkClean
}

internal class FakeAppPermissionScanRepository(
    var reports: List<AppPermissionReport> = emptyList(),
) : AppPermissionScanRepository {

    var scanError: AppError? = null
    var refreshResult: AppPermissionReport? = null
    val refreshed = mutableListOf<String>()

    override suspend fun scanAll(): AppResult<ImmutableList<AppPermissionReport>> =
        scanError?.let { AppResult.Failure(it) } ?: AppResult.Success(reports.toImmutableList())

    override suspend fun refresh(packageName: String): AppResult<AppPermissionReport?> {
        refreshed += packageName
        return AppResult.Success(refreshResult)
    }
}

internal class FakeFeatureStatsRepository : FeatureStatsRepository {
    val counts = mutableListOf<Int>()

    override suspend fun setSensitiveAppCount(count: Int) {
        counts += count
    }

    override fun observeSensitiveAppCount(): Flow<Int?> = MutableStateFlow(null)
}

// ---------------------------------------------------------------------------------------------
// Builders. Named arguments at 30 call sites are what these replace.
// ---------------------------------------------------------------------------------------------

internal fun settings(
    master: Boolean = true,
    apps: List<NotificationHidingApp> = emptyList(),
) = NotificationHidingSettings(master, apps.toImmutableList())

internal fun hidingApp(packageName: String, enabled: Boolean = false) =
    NotificationHidingApp(packageName, packageName.substringAfterLast('.'), enabled)

internal fun hidden(key: String, packageName: String = "com.example.chat", postedAt: Long = 0L) =
    HiddenNotification(key, packageName, "title-$key", "body-$key", Instant.fromEpochMilliseconds(postedAt))

internal fun report(
    packageName: String,
    sensitive: List<String> = emptyList(),
) = AppPermissionReport(
    packageName = packageName,
    label = packageName.substringAfterLast('.'),
    sensitive = sensitive.map { GrantedPermission(it, null, null) }.toImmutableList(),
    normal = persistentListOf(),
    other = persistentListOf(),
)
