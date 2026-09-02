package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.collections.immutable.ImmutableList

/**
 * One album's photos, **queried** from its folder name
 * (`docs/screens/13-photo-and-media.md` §7.2).
 *
 * The competitor mutates a single process-wide `ce.b` instance created at class load and reads it
 * back on the next screen (§7.5). A static cannot survive process death and cannot be typed; a route
 * argument plus a query can do both.
 */
class LoadAlbumPhotosUseCase(
    private val photos: PhotoRepository,
) {
    suspend operator fun invoke(folderName: String): AppResult<ImmutableList<Photo>> =
        photos.photosInFolder(folderName)
}
