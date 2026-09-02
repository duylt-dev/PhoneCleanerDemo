package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.photo.GeotagScanProgress
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.StripStep
import kotlinx.coroutines.flow.Flow

/**
 * Which photos carry a location, and the removal of it
 * (`docs/screens/13-photo-and-media.md` §0.3, §5.2).
 *
 * **The file keeps its name.** `nd/e.i` builds `IMG_0042_1756694400000.jpg` and calls `renameTo` as
 * an *argument* to `MediaScannerConnection.scanFile`, so every cleaned photo comes back under a new
 * name and the model row still holds the old path — a second pass then finds nothing (§5.5).
 */
interface ExifRepository {

    /** See [GeotagScanProgress] for why this reports progress instead of returning a list. */
    fun photosWithLocation(): Flow<GeotagScanProgress>

    /** One [StripStep] per photo. `failed` is real: the competitor's engine returns success always. */
    fun stripLocation(ids: List<PhotoId>): Flow<StripStep>
}
