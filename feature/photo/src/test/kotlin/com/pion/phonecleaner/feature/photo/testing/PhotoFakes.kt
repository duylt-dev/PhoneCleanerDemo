package com.pion.phonecleaner.feature.photo.testing

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.photo.CompressStep
import com.pion.phonecleaner.domain.model.photo.CompressionEstimate
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.CompressedPhotoLedger
import com.pion.phonecleaner.domain.repository.FeatureUsageRepository
import com.pion.phonecleaner.domain.repository.PhotoCompressor
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Instant

/**
 * Hand-written fakes. **No mocking library** — `LLM.md` §9 keeps every fixture readable and every
 * failure a real assertion rather than a stub-verification.
 */
internal fun photo(
    id: Long,
    sizeBytes: Long = 1_000L,
    folderName: String = "/storage/emulated/0/DCIM",
    takenAtMillis: Long = id * 1_000L,
): Photo = Photo(
    id = PhotoId(id),
    contentUri = "content://media/external/images/media/$id",
    displayName = "IMG_$id.jpg",
    folderName = folderName,
    sizeBytes = sizeBytes,
    takenAt = Instant.fromEpochMilliseconds(takenAtMillis),
)

internal fun group(key: String, vararg photos: Photo): PhotoGroup =
    PhotoGroup(key = key, label = key, photos = photos.toList().toImmutableList())

internal fun album(
    folderName: String,
    count: Int,
    totalBytes: Long = count * 1_000L,
    coverUri: String? = "content://media/external/images/media/1",
): PhotoAlbum = PhotoAlbum(
    folderName = folderName,
    coverUri = coverUri,
    count = count,
    totalBytes = totalBytes,
)

internal class FakePhotoRepository(
    var library: List<Photo> = emptyList(),
) : PhotoRepository {
    var nextDelete: AppResult<DeleteOutcome> =
        AppResult.Success(DeleteOutcome.Deleted(persistentListOf(), 0L, persistentListOf()))
    var deletedIds: List<PhotoId> = emptyList()
    var photosFailure: AppError? = null

    /** The album index is a feed: `AlbumsViewModel` collects it once and never re-enters (§6.2). */
    val albums = MutableStateFlow<AppResult<ImmutableList<PhotoAlbum>>>(
        AppResult.Success(persistentListOf()),
    )

    override suspend fun photos(): AppResult<ImmutableList<Photo>> =
        photosFailure?.let { AppResult.Failure(it) } ?: AppResult.Success(library.toImmutableList())

    override fun observeAlbums(): Flow<AppResult<ImmutableList<PhotoAlbum>>> = albums

    override suspend fun photosInFolder(folderName: String): AppResult<ImmutableList<Photo>> =
        AppResult.Success(library.filter { it.folderName == folderName }.toImmutableList())

    override suspend fun delete(ids: List<PhotoId>): AppResult<DeleteOutcome> {
        deletedIds = ids
        return nextDelete
    }

    /**
     * The same `Photo -> ScannedFile` shape `MediaStorePhotoRepository.resolve` builds — `id =
     * contentUri`, so `DeleteOutcome.Deleted.ids` still maps back to `photo.contentUri in outcome.ids`
     * in every reducer under test (plan `260908-0801-trash-bin`, Phase 07).
     */
    override suspend fun resolve(ids: List<PhotoId>): AppResult<ImmutableList<ScannedFile>> {
        val wanted = ids.toSet()
        return AppResult.Success(
            library.filter { it.id in wanted }.map { it.toFakeScannedFile() }.toImmutableList(),
        )
    }
}

private fun Photo.toFakeScannedFile(): ScannedFile = ScannedFile(
    id = contentUri,
    path = "$folderName/$displayName",
    name = displayName,
    sizeBytes = sizeBytes,
    kind = FileKind.Image,
    origin = FileOrigin.MediaStoreEntry(contentUri),
)

// FakeTrashRepository and FakePermissionRepository live in PhotoTrashFakes.kt — split out the way
// PhotoScanFakes.kt already is, one file per concern (plan 260908-0801-trash-bin, Phase 07).

internal class FakePhotoCompressor(
    var steps: List<CompressStep> = emptyList(),
    /** Emit [steps] and then never complete — a run still in flight. */
    var hangs: Boolean = false,
    var estimate: AppResult<CompressionEstimate> =
        AppResult.Success(CompressionEstimate(beforeBytes = 0L, afterBytes = 0L, sampledCount = 0)),
) : PhotoCompressor {
    var estimatedIds: List<PhotoId> = emptyList()

    override fun compress(ids: List<PhotoId>, quality: Int, maxEdgePx: Int): Flow<CompressStep> =
        flow {
            steps.forEach { emit(it) }
            if (hangs) awaitCancellation()
        }

    override suspend fun estimate(
        ids: List<PhotoId>,
        sampleSize: Int,
        quality: Int,
        maxEdgePx: Int,
    ): AppResult<CompressionEstimate> {
        estimatedIds = ids
        return estimate
    }
}

/**
 * The ledger of already re-encoded photos, in memory. Seed [ids] to stand for a previous run.
 */
internal class FakeCompressedPhotoLedger(
    ids: Set<PhotoId> = emptySet(),
) : CompressedPhotoLedger {
    val ids: MutableSet<PhotoId> = ids.toMutableSet()

    override suspend fun compressedIds(): Set<PhotoId> = ids.toSet()

    override suspend fun record(id: PhotoId) {
        ids += id
    }
}

internal class RecordingAnalytics : AnalyticsRepository {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events.add(event)
    }
}

internal class RecordingFeatureUsage : FeatureUsageRepository {
    val used = mutableListOf<FeatureId>()
    override suspend fun markUsed(feature: FeatureId) {
        used.add(feature)
    }

    override fun lastUsed(feature: FeatureId) = emptyFlow<Instant?>()
    override fun staleFeatures() = emptyFlow<ImmutableList<FeatureId>>()
    override suspend fun recommend(): FeatureId = FeatureId.JunkClean
}
