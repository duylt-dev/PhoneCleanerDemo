package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.PhotoRepository
import com.pion.phonecleaner.domain.testing.FakeTrashRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DeletePhotosUseCaseTest {
    private val photos = DeletePhotoFake()
    private val trash = FakeTrashRepository(movedBytes = 10)
    private val useCase = DeletePhotosUseCase(photos, trash)
    private val ids = listOf(PhotoId(1))

    @Test fun `confirmed move resolves photos and reports original media ids as recoverable`() = runTest {
        val result = useCase(ids, FeatureId.BlurryPhotos, requireTrash = true) as AppResult.Success
        val deleted = result.value as DeleteOutcome.Deleted
        assertEquals(listOf("content://image/1"), deleted.ids)
        assertTrue(deleted.recoverable)
        assertEquals(10L, deleted.freedBytes)
        assertEquals(0, photos.deleteCalls)
        assertEquals(ids, photos.resolvedIds)
    }

    @Test fun `permission loss after trash confirmation refuses permanently deleting photos`() = runTest {
        trash.isAvailable = false
        assertTrue(useCase(ids, FeatureId.BlurryPhotos, requireTrash = true) is AppResult.Failure)
        assertEquals(0, photos.deleteCalls)
    }

    @Test fun `explicit permanent confirmation preserves repository consent protocol`() = runTest {
        val result = useCase(ids, FeatureId.SimilarPhotos, requireTrash = false)
        assertEquals(photos.deleteResult, result)
        assertEquals(1, photos.deleteCalls)
        assertTrue(photos.resolvedIds.isEmpty())
        assertTrue(trash.movedIds.isEmpty())
    }

    @Test fun `resolution failure touches neither mover nor permanent deleter`() = runTest {
        photos.resolveResult = AppResult.Failure(AppError.PermissionDenied())
        assertEquals(photos.resolveResult, useCase(ids, FeatureId.ImageManager, requireTrash = true))
        assertTrue(trash.movedIds.isEmpty())
        assertEquals(0, photos.deleteCalls)
    }

    @Test fun `resolution cancellation propagates without falling back to delete`() = runTest {
        photos.cancel = true
        try {
            useCase(ids, FeatureId.ImageManager, requireTrash = true)
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(0, photos.deleteCalls)
            assertTrue(trash.movedIds.isEmpty())
        }
    }
}

private class DeletePhotoFake : PhotoRepository {
    var deleteCalls = 0
    var resolvedIds: List<PhotoId> = emptyList()
    var cancel = false
    val deleteResult: AppResult<DeleteOutcome> = AppResult.Success(DeleteOutcome.NothingResolved)
    var resolveResult: AppResult<ImmutableList<ScannedFile>> = AppResult.Success(persistentListOf(
        ScannedFile("content://image/1", "Pictures/", "photo.jpg", 10, FileKind.Image,
            origin = FileOrigin.MediaStoreEntry("content://image/1")),
    ))
    override suspend fun delete(ids: List<PhotoId>): AppResult<DeleteOutcome> {
        deleteCalls++
        return deleteResult
    }
    override suspend fun resolve(ids: List<PhotoId>): AppResult<ImmutableList<ScannedFile>> {
        if (cancel) throw CancellationException("cancelled resolution")
        resolvedIds = ids
        return resolveResult
    }
    override suspend fun photos(): AppResult<ImmutableList<Photo>> = error("Not used")
    override fun observeAlbums(): Flow<AppResult<ImmutableList<PhotoAlbum>>> = error("Not used")
    override suspend fun photosInFolder(folderName: String): AppResult<ImmutableList<Photo>> = error("Not used")
}
