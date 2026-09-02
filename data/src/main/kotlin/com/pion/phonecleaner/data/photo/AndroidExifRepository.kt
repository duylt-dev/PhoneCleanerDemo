package com.pion.phonecleaner.data.photo

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.photo.GeotagScanProgress
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.StripStep
import com.pion.phonecleaner.domain.policy.PhotoGrouping
import com.pion.phonecleaner.domain.repository.ExifRepository
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Which photos carry a location, and the removal of it
 * (`docs/screens/13-photo-and-media.md` §5.2, §5.5).
 *
 * Three differences from `nd/e`, all of them user-visible:
 *
 *  1. **The candidates are MIME-filtered** by `PhotoRepository.photos()`. `nd/e` queries
 *     `MediaStore.Images` with no MIME filter — unlike its two sibling engines — and opens an
 *     `ExifInterface` on every image row on the device, sequentially.
 *  2. **The file keeps its name.** `nd/e.i` builds `IMG_0042_1756694400000.jpg` and calls `renameTo`
 *     as an *argument* to `MediaScannerConnection.scanFile`, so every cleaned photo comes back under
 *     a new name and the model still holds the old path — a second pass then finds nothing.
 *  3. **A failure is reported.** `nd/e.a` catches, prints, and counts the row as a success anyway,
 *     which is what makes its own "Delete Failed" toast unreachable.
 */
internal class AndroidExifRepository(
    private val context: Context,
    private val photos: PhotoRepository,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : ExifRepository {

    override fun photosWithLocation(): Flow<GeotagScanProgress> = flow {
        val rows = when (val result = photos.photos()) {
            is AppResult.Failure -> {
                emit(GeotagScanProgress.Failed(result.error))
                return@flow
            }

            is AppResult.Success -> result.value
        }
        emit(GeotagScanProgress.Scanning(scanned = 0, total = rows.size))
        if (rows.isEmpty()) {
            emit(GeotagScanProgress.Done(persistentListOf()))
            return@flow
        }
        val located = ArrayList<Photo>()
        rows.forEachIndexed { index, photo ->
            if (hasLocation(photo)) located += photo
            if ((index + 1) % PROGRESS_BATCH == 0 || index == rows.lastIndex) {
                emit(GeotagScanProgress.Scanning(scanned = index + 1, total = rows.size))
            }
        }
        emit(GeotagScanProgress.Done(PhotoGrouping.byMonth(located)))
    }.flowOn(dispatchers.io)

    override fun stripLocation(ids: List<PhotoId>): Flow<StripStep> = flow {
        val rows = photos.rowsFor(ids)
        if (rows is AppResult.Failure) {
            log.e { "Location strip could not read the selection: ${rows.error}" }
            ids.forEachIndexed { index, id -> emit(StripStep(index + 1, ids.size, id, failed = true)) }
            return@flow
        }
        val selected = (rows as AppResult.Success).value
        selected.forEachIndexed { index, photo ->
            emit(StripStep(index + 1, selected.size, photo.id, failed = !strip(photo)))
        }
    }.flowOn(dispatchers.io)

    /** Four attributes read through the row's URI. Any exception means "cannot tell", i.e. exclude. */
    private fun hasLocation(photo: Photo): Boolean = runCatching {
        val uri = Uri.parse(photo.contentUri)
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            GPS_PRESENCE_TAGS.any { tag -> !exif.getAttribute(tag).isNullOrEmpty() }
        } ?: false
    }.getOrDefault(false)

    /**
     * `saveAttributes()` needs a seekable, writable handle, which is what `openFileDescriptor(uri,
     * "rw")` is. On a row this app does not own that raises a `SecurityException`, and the photo is
     * then reported as failed rather than counted as cleared — see §5.5.
     */
    private fun strip(photo: Photo): Boolean = runCatching {
        val uri = Uri.parse(photo.contentUri)
        context.contentResolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
            val exif = ExifInterface(descriptor.fileDescriptor)
            GPS_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
            exif.saveAttributes()
            true
        } ?: false
    }.getOrElse { cause ->
        log.e(cause) { "Could not clear location on ${photo.id.value}" }
        false
    }

    private companion object {
        /** How often the counter moves. One emission per photo would be 5 000 emissions. */
        const val PROGRESS_BATCH = 16
    }
}
