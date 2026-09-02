package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.StripStep
import com.pion.phonecleaner.domain.repository.ExifRepository
import kotlinx.coroutines.flow.Flow

/**
 * Removes the location tags from the selected photos
 * (`docs/screens/13-photo-and-media.md` §5.2).
 *
 * It frees no bytes, and that is a correct outcome the shared result screen already has a name for:
 * `CleanupOutcome.DataCleared` (§5.2, `CleanupSummary`).
 */
class StripPhotoLocationUseCase(
    private val exif: ExifRepository,
) {
    operator fun invoke(ids: List<PhotoId>): Flow<StripStep> = exif.stripLocation(ids)
}
