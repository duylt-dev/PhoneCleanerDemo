package com.pion.phonecleaner.data.photo

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.photo.BlurScanProgress
import com.pion.phonecleaner.domain.model.photo.BlurTier
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.policy.BlurPolicy
import com.pion.phonecleaner.domain.repository.BlurDetector
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

/**
 * The grouping half of the engine, with the detector faked so the scan is three numbers and no
 * `Bitmap`. `LaplacianVarianceTest` covers the arithmetic; this covers what the scan *does* with it.
 */
class DefaultBlurryPhotoScannerTest {

    private fun photo(id: Long) = Photo(
        id = PhotoId(id),
        contentUri = "content://media/external/images/media/$id",
        displayName = "IMG_$id.jpg",
        folderName = "DCIM/Camera",
        sizeBytes = 1_000L,
        takenAt = Instant.fromEpochMilliseconds(id * 1_000L),
    )

    private fun scannerOver(
        rows: List<Photo>,
        scores: Map<Long, Double?>,
        failure: AppError? = null,
    ) = DefaultBlurryPhotoScanner(
        photos = FakePhotos(rows, failure),
        detector = FakeDetector(scores),
        dispatchers = TestDispatchers,
    )

    private suspend fun done(progress: List<BlurScanProgress>) =
        progress.filterIsInstance<BlurScanProgress.Done>().single()

    @Test
    fun `photos are split into the two tiers, in tier order`() = runTest {
        val rows = listOf(photo(1), photo(2), photo(3))
        val scanner = scannerOver(
            rows,
            mapOf(1L to 10.0, 2L to 200.0, 3L to 5_000.0),
        )

        val result = done(scanner.scan().toList())

        assertEquals(
            listOf(BlurTier.VeryBlurry.name, BlurTier.SlightlyBlurry.name),
            result.groups.map { it.key },
        )
        assertEquals(listOf(PhotoId(1)), result.groups[0].photos.map { it.id })
        assertEquals(listOf(PhotoId(2)), result.groups[1].photos.map { it.id })
    }

    /** A sharp photo is not a row on this screen at all — it is in no group. */
    @Test
    fun `photos above the sharp threshold are in no group`() = runTest {
        val scanner = scannerOver(listOf(photo(1)), mapOf(1L to BlurPolicy.BLURRY_BELOW))

        val result = done(scanner.scan().toList())

        assertTrue(result.groups.isEmpty())
        assertEquals(0, result.skipped)
    }

    @Test
    fun `an empty tier produces no group rather than an empty one`() = runTest {
        // Only very-blurry photos: the SlightlyBlurry header must not be drawn over nothing.
        val scanner = scannerOver(listOf(photo(1), photo(2)), mapOf(1L to 1.0, 2L to 2.0))

        val result = done(scanner.scan().toList())

        assertEquals(1, result.groups.size)
        assertEquals(BlurTier.VeryBlurry.name, result.groups.single().key)
    }

    @Test
    fun `each tier is ordered blurriest first`() = runTest {
        val rows = listOf(photo(1), photo(2), photo(3))
        val scanner = scannerOver(rows, mapOf(1L to 90.0, 2L to 10.0, 3L to 50.0))

        val result = done(scanner.scan().toList())

        assertEquals(
            listOf(PhotoId(2), PhotoId(3), PhotoId(1)),
            result.groups.single().photos.map { it.id },
        )
    }

    /**
     * The most dangerous bug this screen could have. A `null` score means "not measured" — an
     * unreadable file, or one too small to judge — and it must be **excluded**, never treated as
     * `0.0`, which is the blurriest possible reading and would pre-tick it for deletion.
     */
    @Test
    fun `an unmeasurable photo is counted as skipped and lands in no tier`() = runTest {
        val rows = listOf(photo(1), photo(2))
        val scanner = scannerOver(rows, mapOf(1L to null, 2L to 10.0))

        val result = done(scanner.scan().toList())

        assertEquals(1, result.skipped)
        assertEquals(listOf(PhotoId(2)), result.groups.single().photos.map { it.id })
    }

    /** One hostile file must not cost the other 4 999 photos. */
    @Test
    fun `a detector that throws costs one skip, not the whole scan`() = runTest {
        val rows = listOf(photo(1), photo(2))
        val scanner = DefaultBlurryPhotoScanner(
            photos = FakePhotos(rows, null),
            detector = ThrowingDetector(throwsFor = "content://media/external/images/media/1"),
            dispatchers = TestDispatchers,
        )

        val result = done(scanner.scan().toList())

        assertEquals(1, result.skipped)
        assertEquals(listOf(PhotoId(2)), result.groups.single().photos.map { it.id })
    }

    @Test
    fun `progress counts every photo, measured or not`() = runTest {
        val rows = (1L..3L).map { photo(it) }
        val scanner = scannerOver(rows, mapOf(1L to null, 2L to 10.0, 3L to 10.0))

        val scoring = scanner.scan().toList().filterIsInstance<BlurScanProgress.Scoring>()

        assertEquals(0, scoring.first().done)
        assertEquals(3, scoring.last().done)
        assertTrue(scoring.all { it.total == 3 })
    }

    @Test
    fun `a repository failure is an arm, not a thrown exception`() = runTest {
        val scanner = scannerOver(emptyList(), emptyMap(), failure = AppError.PermissionDenied())

        val progress = scanner.scan().toList()

        assertEquals(
            AppError.PermissionDenied(),
            (progress.single() as BlurScanProgress.Failed).error,
        )
    }

    @Test
    fun `an empty library finishes with no groups rather than hanging`() = runTest {
        val result = done(scannerOver(emptyList(), emptyMap()).scan().toList())

        assertTrue(result.groups.isEmpty())
    }

    private class FakeDetector(private val scores: Map<Long, Double?>) : BlurDetector {
        override suspend fun score(uri: String): Double? =
            scores[uri.substringAfterLast('/').toLong()]
    }

    private class ThrowingDetector(private val throwsFor: String) : BlurDetector {
        override suspend fun score(uri: String): Double? {
            if (uri == throwsFor) throw OutOfMemoryError("hostile file")
            return 10.0
        }
    }

    private class FakePhotos(
        private val rows: List<Photo>,
        private val failure: AppError?,
    ) : PhotoRepository {
        override suspend fun photos(): AppResult<ImmutableList<Photo>> =
            failure?.let { AppResult.Failure(it) } ?: AppResult.Success(rows.toImmutableList())

        override fun observeAlbums(): Flow<AppResult<ImmutableList<PhotoAlbum>>> = emptyFlow()

        override suspend fun photosInFolder(folderName: String): AppResult<ImmutableList<Photo>> =
            AppResult.Success(persistentListOf())

        override suspend fun delete(ids: List<PhotoId>): AppResult<DeleteOutcome> =
            AppResult.Success(DeleteOutcome.NothingResolved)

        /** Not exercised by this test (the blur scan never deletes); kept honest rather than stubbed. */
        override suspend fun resolve(ids: List<PhotoId>): AppResult<ImmutableList<ScannedFile>> {
            val wanted = ids.toSet()
            return AppResult.Success(
                rows.filter { it.id in wanted }.map {
                    ScannedFile(
                        id = it.contentUri,
                        path = "${it.folderName}/${it.displayName}",
                        name = it.displayName,
                        sizeBytes = it.sizeBytes,
                        kind = FileKind.Image,
                        origin = FileOrigin.MediaStoreEntry(it.contentUri),
                    )
                }.toImmutableList(),
            )
        }
    }

    private object TestDispatchers : DispatcherProvider {
        override val main: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val io: CoroutineDispatcher = UnconfinedTestDispatcher()
        override val default: CoroutineDispatcher = UnconfinedTestDispatcher()
    }
}
