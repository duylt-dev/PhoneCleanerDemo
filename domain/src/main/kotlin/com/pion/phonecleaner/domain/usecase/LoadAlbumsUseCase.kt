package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The album index, as a feed (`docs/screens/13-photo-and-media.md` §6.2).
 *
 * A `Flow`, so a photo added or deleted elsewhere updates the screen without a re-entry. Sorting is
 * the screen's reducer's job, not this one's.
 */
class LoadAlbumsUseCase(
    private val photos: PhotoRepository,
) {
    operator fun invoke(): Flow<AppResult<ImmutableList<PhotoAlbum>>> = photos.observeAlbums()
}
