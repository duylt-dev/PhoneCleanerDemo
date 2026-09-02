package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.photo.GeotagScanProgress
import com.pion.phonecleaner.domain.repository.ExifRepository
import kotlinx.coroutines.flow.Flow

/**
 * Photos carrying GPS tags, bucketed by month (`docs/screens/13-photo-and-media.md` §5.2).
 *
 * The candidate query is MIME-filtered by `PhotoRepository.photos()`; the competitor opens an
 * `ExifInterface` on **every** image row, sequentially
 * (`docs/reverse-engineering/13-photo-and-media.md` §4.4).
 */
class LoadGeotaggedPhotosUseCase(
    private val exif: ExifRepository,
) {
    operator fun invoke(): Flow<GeotagScanProgress> = exif.photosWithLocation()
}
