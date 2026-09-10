package com.pion.phonecleaner.feature.files.testing

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.app.AppStorageStats
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.model.device.StorageInfo
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.model.file.WhatsAppScanProgress
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.trash.TrashDirectoryRequest
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashMoveOutcome
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.domain.model.trash.TrashSummary
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressProgress
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.AppControlRepository
import com.pion.phonecleaner.domain.repository.AppStorageStatsRepository
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.CompressedVideoLedger
import com.pion.phonecleaner.domain.repository.DuplicateFinder
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.InstalledAppsRepository
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.StorageInfoRepository
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import com.pion.phonecleaner.domain.repository.TrashRepository
import com.pion.phonecleaner.domain.repository.VideoCandidateRepository
import com.pion.phonecleaner.domain.repository.VideoCompressor
import com.pion.phonecleaner.domain.repository.VideoEncoderCapabilities
import com.pion.phonecleaner.domain.repository.WhatsAppScanner
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
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

/**
 * [available] answers `isAvailable()`; defaults to `false` so an existing test that never sets it
 * keeps exercising the permanent-delete branch unchanged (plan `260908-0801-trash-bin`, Phase 07).
 * [moveOutcomes] is a queue, the same shape as [FakeFileDeleter.outcomes]. Only [isAvailable],
 * [trashFiles] and [trashDirectory] are exercised from this module; the rest of [TrashRepository]
 * belongs to `:feature:trash` and is stubbed only enough to implement the interface.
 */
internal class FakeTrashRepository(
    var available: Boolean = false,
    var moveOutcomes: MutableList<AppResult<TrashMoveOutcome>> = mutableListOf(),
) : TrashRepository {
    val trashFilesCalls = mutableListOf<Pair<List<ScannedFile>, FeatureId>>()
    val trashDirectoryCalls = mutableListOf<TrashDirectoryRequest>()

    override suspend fun isAvailable(): Boolean = available

    override suspend fun trashFiles(
        files: List<ScannedFile>,
        source: FeatureId,
    ): AppResult<TrashMoveOutcome> {
        trashFilesCalls += files to source
        return moveOutcomes.removeFirstOrNull()
            ?: AppResult.Success(
                TrashMoveOutcome(
                    movedIds = files.map(ScannedFile::id).toImmutableList(),
                    movedBytes = files.sumOf(ScannedFile::sizeBytes),
                    failedPaths = persistentListOf(),
                ),
            )
    }

    override suspend fun trashDirectory(request: TrashDirectoryRequest): AppResult<TrashMoveOutcome> {
        trashDirectoryCalls += request
        return moveOutcomes.removeFirstOrNull()
            ?: AppResult.Success(
                TrashMoveOutcome(movedIds = persistentListOf(request.path), movedBytes = 0L, failedPaths = persistentListOf()),
            )
    }

    override fun observeEntries(): Flow<ImmutableList<TrashEntry>> = MutableStateFlow(persistentListOf())
    override fun observeSummary(): Flow<TrashSummary> = MutableStateFlow(TrashSummary())
    override suspend fun restore(ids: List<String>): AppResult<TrashRestoreOutcome> =
        AppResult.Success(TrashRestoreOutcome(persistentListOf(), persistentListOf(), 0))

    override suspend fun deleteForever(ids: List<String>): AppResult<TrashPurgeOutcome> =
        AppResult.Success(TrashPurgeOutcome(persistentListOf(), 0L, persistentListOf()))

    override suspend fun deleteAllForever(): AppResult<TrashPurgeOutcome> = deleteForever(emptyList())

    override suspend fun purgeExpired(): AppResult<TrashPurgeOutcome> =
        AppResult.Success(TrashPurgeOutcome(persistentListOf(), 0L, persistentListOf()))

    override suspend fun reconcile(): AppResult<Int> = AppResult.Success(0)
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

/**
 * `videocompressor`/`videocompressrun` fakes (phase 09). [pool] backs both [candidates] and
 * [rowsFor]; [rowsFor] returns rows in the CALLER's id order, never the pool's storage order — same
 * contract `VideoCandidateRepository.rowsFor`'s own KDoc states.
 */
internal class FakeVideoCandidateRepository(
    var pool: List<VideoCandidate> = emptyList(),
    var failure: AppResult.Failure? = null,
    /** Never answers — the picker's stuck-state/timeout test. */
    var hangs: Boolean = false,
    var throwOnCandidates: Boolean = false,
) : VideoCandidateRepository {
    var calls = 0
        private set

    override suspend fun candidates(): AppResult<ImmutableList<VideoCandidate>> {
        calls++
        if (hangs) awaitCancellation()
        if (throwOnCandidates) throw IllegalStateException("FakeVideoCandidateRepository: forced failure")
        return failure ?: AppResult.Success(pool.toImmutableList())
    }

    override suspend fun rowsFor(ids: List<String>): AppResult<ImmutableList<VideoCandidate>> =
        AppResult.Success(ids.mapNotNull { id -> pool.firstOrNull { it.id == id } }.toImmutableList())
}

internal class FakeCompressedVideoLedger(
    ids: Set<String> = emptySet(),
) : CompressedVideoLedger {
    val recorded: MutableSet<String> = ids.toMutableSet()
    override suspend fun compressedIds(): Set<String> = recorded
    override suspend fun record(id: String) {
        recorded += id
    }
}

/**
 * [gate] is what makes "observe the run **while it is still running**" possible — the state a Stop
 * has to be correct about — the same reason [FakeFileDeleter] already has one. The flow emits every
 * queued [emissions] item and then parks on [gate] instead of completing, so a test can cancel the
 * collecting job mid-run rather than only ever after it has already finished.
 */
internal class FakeVideoCompressor(
    var emissions: List<VideoCompressProgress> = emptyList(),
    var gate: CompletableDeferred<Unit>? = null,
    var throwOnCollect: Boolean = false,
) : VideoCompressor {
    var calls = 0
        private set
    var lastIds: List<String> = emptyList()
        private set
    var lastPreset: VideoQualityPreset? = null
        private set
    var lastCodec: VideoCodecOption? = null
        private set

    override fun compress(
        ids: List<String>,
        preset: VideoQualityPreset,
        codec: VideoCodecOption,
    ): Flow<VideoCompressProgress> = flow {
        calls++
        lastIds = ids
        lastPreset = preset
        lastCodec = codec
        if (throwOnCollect) throw IllegalStateException("FakeVideoCompressor: forced failure")
        emissions.forEach { emit(it) }
        gate?.await()
    }
}

/** So a space refusal is one line: set [available] below what the estimate + the floor need. */
internal class FakeStorageInfoRepository(
    var available: Long = 10L * 1024L * 1024L * 1024L,
    var total: Long = 64L * 1024L * 1024L * 1024L,
    var failure: AppResult.Failure? = null,
) : StorageInfoRepository {
    override fun observe(): Flow<StorageInfo> = MutableStateFlow(StorageInfo(total, available))
    override suspend fun current(): AppResult<StorageInfo> =
        failure ?: AppResult.Success(StorageInfo(totalBytes = total, availableBytes = available))
}

/**
 * Answers a question ABOUT A CODEC, exactly like `MediaCodecVideoEncoderCapabilities`, rather than
 * exposing a bare flag the test reads directly — a fake whose shape differs from the real class stops
 * proving anything the day the port grows a third constant.
 *
 * `H264` is hard-wired `true` in every instance (D6, Phase 04 step 3b): the real device probe never
 * asks about H.264, and a fake that could say otherwise would be testing a device this app cannot
 * serve. [hevcSupported] is the only knob, and it is the ONLY coverage the HEVC-disabled state has —
 * the real test device (Samsung SM-A165F) carries a hardware HEVC encoder and can never render that
 * state on its own.
 */
internal class FakeVideoEncoderCapabilities(
    var hevcSupported: Boolean = true,
) : VideoEncoderCapabilities {
    override suspend fun isSupported(codec: VideoCodecOption): Boolean = when (codec) {
        VideoCodecOption.H264 -> true
        VideoCodecOption.Hevc -> hevcSupported
    }
}
