package com.pion.phonecleaner.feature.files.testing

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.app.AppStorageStats
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.model.file.WhatsAppScanProgress
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.AppControlRepository
import com.pion.phonecleaner.domain.repository.AppStorageStatsRepository
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.DuplicateFinder
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import com.pion.phonecleaner.domain.repository.WhatsAppScanner
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

/**
 * Hand-written fakes, no mocking library (`LLM.md` §9). A fake you can read beats a mock you have to
 * decode, and every one of these is small enough to read in one screen.
 */
internal class FakeDuplicateFinder(
    var emissions: List<DuplicateScanProgress> = emptyList(),
) : DuplicateFinder {
    var calls = 0
        private set

    override fun find(): Flow<DuplicateScanProgress> = flow {
        calls++
        emissions.forEach { emit(it) }
    }
}

/**
 * [outcomes] is a queue, so one test can answer `PendingConsent` and then `Deleted`.
 *
 * [gate] holds the first batch open, which is the only way to observe a clean **while it is still
 * running** — the state a cancel has to be correct about.
 */
internal class FakeFileDeleter(
    var outcomes: MutableList<AppResult<DeleteOutcome>> = mutableListOf(),
    var gate: CompletableDeferred<Unit>? = null,
) : FileDeleter {
    val requested = mutableListOf<List<ScannedFile>>()

    override suspend fun delete(files: List<ScannedFile>): AppResult<DeleteOutcome> {
        requested += files
        gate?.await()
        return outcomes.removeFirstOrNull()
            ?: AppResult.Success(
                DeleteOutcome.Deleted(
                    ids = files.map(ScannedFile::id).toImmutableList(),
                    freedBytes = files.sumOf(ScannedFile::sizeBytes),
                    failedPaths = persistentListOf(),
                ),
            )
    }
}

internal class FakeCleanupLedger : CleanupLedger {
    val lifetime = MutableStateFlow(0L)
    override suspend fun record(freedBytes: Long): AppResult<Unit> {
        lifetime.value += freedBytes
        return AppResult.Success(Unit)
    }

    override fun observeLifetimeFreedBytes(): Flow<Long> = lifetime
}

internal class FakeAnalyticsRepository : AnalyticsRepository {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) { events += event }
}

internal class FakeFeatureUsageRepository : FeatureUsageRepository {
    val marked = mutableListOf<FeatureId>()
    override suspend fun markUsed(feature: FeatureId) { marked += feature }
    override fun lastUsed(feature: FeatureId): Flow<Instant?> = MutableStateFlow(null)
    override fun staleFeatures(): Flow<ImmutableList<FeatureId>> =
        MutableStateFlow(persistentListOf())

    override suspend fun recommend(): FeatureId = FeatureId.BigFiles
}

internal class FakePermissionRepository : PermissionRepository {
    val granted = MutableStateFlow<ImmutableSet<AppPermission>>(persistentSetOf())
    override fun observe(): Flow<ImmutableSet<AppPermission>> = granted
    override fun isGranted(permission: AppPermission): Boolean = permission in granted.value
    override fun missingFor(feature: FeatureId): ImmutableSet<AppPermission> = persistentSetOf()
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

internal class FakeAppStorageStatsRepository(
    var stats: List<AppStorageStats> = emptyList(),
) : AppStorageStatsRepository {
    override fun statsFor(apps: List<InstalledApp>): Flow<AppStorageStats> = flow {
        stats.forEach { emit(it) }
    }
}

/** [installed] is reassignable so one test can uninstall an app between two round trips. */
internal class FakeAppControlRepository(
    var installed: MutableSet<String> = mutableSetOf(),
) : AppControlRepository {
    override suspend fun isInstalled(packageName: String): Boolean = packageName in installed
}

internal class FakeWhatsAppScanner(
    var emissions: List<WhatsAppScanProgress> = emptyList(),
) : WhatsAppScanner {
    override fun scan(): Flow<WhatsAppScanProgress> = flow { emissions.forEach { emit(it) } }
}

internal class FakeMediaStoreRepository(
    var images: AppResult<ImmutableList<ScannedFile>> = AppResult.Success(persistentListOf()),
    var videos: AppResult<ImmutableList<ScannedFile>> = AppResult.Success(persistentListOf()),
    var audio: AppResult<ImmutableList<ScannedFile>> = AppResult.Success(persistentListOf()),
) : MediaStoreRepository {
    override suspend fun images(): AppResult<ImmutableList<ScannedFile>> = images
    override suspend fun videos(): AppResult<ImmutableList<ScannedFile>> = videos
    override suspend fun audio(): AppResult<ImmutableList<ScannedFile>> = audio
}

internal class FakeStorageScanner(
    var files: List<ScannedFile> = emptyList(),
) : StorageScanner {
    override fun walk(config: WalkConfig): Flow<ScannedFile> = flow {
        files.forEach { emit(it) }
    }
}

internal class FakeStorageRootProvider(
    var roots: ImmutableList<String> = persistentListOf(),
    var surfaces: ImmutableList<String> = persistentListOf(),
) : StorageRootProvider {
    override suspend fun readableRoots(): AppResult<ImmutableList<String>> = AppResult.Success(roots)
    override suspend fun coveredSurfaces(): AppResult<ImmutableList<String>> =
        AppResult.Success(surfaces)
}
