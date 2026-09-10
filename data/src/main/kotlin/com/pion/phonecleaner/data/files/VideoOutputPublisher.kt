package com.pion.phonecleaner.data.files

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.data.storage.MediaStoreQuery
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume

/**
 * The temp file and the publish step (`phase-04-data-media3-engine.md` step 4). Not in Koin — an
 * internal collaborator [Media3VideoCompressor] constructs, the same way
 * [VideoTranscodeSession] is not a `single` either (`filesDataModule`'s own KDoc).
 *
 * **`getExternalFilesDir(DIRECTORY_MOVIES)`, never `cacheDir`/`externalCacheDir`.** Both cache
 * directories are evictable by the system under storage pressure — the exact condition a
 * storage-cleaning app runs in. A transcode losing its output at minute three of four is the worst
 * failure this feature can have; app-private files storage is not evicted.
 */
internal class VideoOutputPublisher(
    private val context: Context,
    private val log: AppLogger,
) {

    /** A unique app-private path. Never shown to the user, so uniqueness matters more than the name. */
    fun newTempFile(sourceName: String, preset: VideoQualityPreset): File {
        val dir = tempDir()
        val base = sourceName.substringBeforeLast('.', sourceName).ifBlank { "video" }
        return File(dir, "${base}_${preset.shortSidePx}p_${System.nanoTime()}.mp4")
    }

    /**
     * Copies [temp] into the public `Movies` collection under [displayName] and returns the new
     * row's `content://` URI as a string, or `null` when publishing failed — in which case nothing is
     * left behind: any row this call inserted is deleted before returning.
     */
    suspend fun publish(temp: File, displayName: String): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) publishScoped(temp, displayName) else publishLegacy(temp, displayName)

    /** Called on every non-success path. Never throws. */
    fun discard(temp: File) {
        runCatching { temp.delete() }
            .onFailure { log.e(it) { "Could not delete temp file ${temp.path}" } }
    }

    /**
     * `VOLUME_EXTERNAL_PRIMARY`, **not** `MediaStoreQuery.volume()`'s read-only `VOLUME_EXTERNAL`
     * union — inserting into that one throws `IllegalArgumentException` at publish time, after the
     * transcode has already spent its minutes.
     */
    private fun publishScoped(temp: File, displayName: String): String? {
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val nowSeconds = System.currentTimeMillis() / 1_000L
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, VIDEO_MIME)
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$APP_SUBDIR")
            put(MediaStore.Video.Media.IS_PENDING, 1)
            put(MediaStore.Video.Media.DATE_ADDED, nowSeconds)
            put(MediaStore.Video.Media.DATE_MODIFIED, nowSeconds)
        }
        val uri = context.contentResolver.insert(collection, values) ?: return null
        return try {
            val opened = context.contentResolver.openOutputStream(uri)?.use { out ->
                temp.inputStream().use { it.copyTo(out) }
            }
            if (opened == null) {
                context.contentResolver.delete(uri, null, null)
                return null
            }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )
            uri.asReadVolumeId()
        } catch (failure: Exception) {
            // Not just IOException: an OEM provider rejecting RELATIVE_PATH throws
            // IllegalArgumentException, and a narrow catch would leave the row stuck at IS_PENDING = 1.
            log.e(failure) { "Publish copy failed for $displayName" }
            // The app owns the row it just inserted, so deleting it needs no consent (Q4).
            runCatching { context.contentResolver.delete(uri, null, null) }
            null
        }
    }

    /**
     * `renameTo` is same-volume and therefore atomic and free; it returns `false` rather than
     * throwing when it cannot, so the copy fallback is mandatory, not defensive noise.
     */
    private suspend fun publishLegacy(temp: File, displayName: String): String? {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES).apply { mkdirs() }
        val target = uniqueLegacyTarget(dir, displayName)
        if (!temp.renameTo(target)) {
            try {
                temp.copyTo(target, overwrite = false)
                temp.delete()
            } catch (io: IOException) {
                log.e(io) { "Legacy publish copy failed for $displayName" }
                return null
            }
        }
        // Bounded. `MediaScannerConnection` can simply never call back, and an unbounded wait parks
        // the whole run at "Preparing…" with no way out but the back button. Timing out reports the
        // step as failed even though the copy exists — the conservative direction, because a failed
        // step never offers the original for deletion.
        return withTimeoutOrNull(SCAN_TIMEOUT_MILLIS) { scanAndAwaitUri(target) }
    }

    /** API 29+ de-duplicates a colliding `DISPLAY_NAME` itself; API 28 has no such behaviour. */
    private fun uniqueLegacyTarget(dir: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.', displayName)
        val ext = displayName.substringAfterLast('.', "")
        var candidate = File(dir, displayName)
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        return candidate
    }

    /**
     * A row inserted before the scanner has seen the file is a gallery entry pointing at nothing, so
     * indexing comes first and the URI comes from the scanner callback, never from a `DATA` insert.
     */
    private suspend fun scanAndAwaitUri(target: File): String? = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(VIDEO_MIME)) { _, uri ->
            if (cont.isActive) cont.resume(uri?.asReadVolumeId())
        }
    }

    /**
     * The row was inserted on `VOLUME_EXTERNAL_PRIMARY`, but the ledger is read back against the ids
     * the *scanner* produces, which are on [MediaStoreQuery]'s read volume. [MediaStoreQuery.videoIdFor]
     * owns that translation for both sides — see its KDoc for what an untranslated id costs.
     */
    private fun Uri.asReadVolumeId(): String = MediaStoreQuery.videoIdFor(this)

    /**
     * A process killed mid-transcode leaves its temp file behind for ever — the name carries
     * `System.nanoTime()` so nothing ever overwrites it, and app-private files storage is, by the
     * design above, deliberately not evictable. A storage-cleaning app quietly hoarding gigabytes of
     * its own is the worst version of this bug, so each run clears what earlier runs abandoned.
     */
    fun sweepAbandonedTempFiles() {
        val cutoff = System.currentTimeMillis() - ABANDONED_AFTER_MILLIS
        runCatching {
            tempDir().listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        }.onFailure { log.e(it) { "Could not sweep abandoned temp files" } }
    }

    /**
     * MUST stay under `DIRECTORY_MOVIES` and MUST stay this app's transcode scratch only.
     *
     * [sweepAbandonedTempFiles] deletes everything older than six hours in here, unconditionally, at
     * the start of every run. The trash root (`data/trash/TrashRoots.kt`) is deliberately elsewhere —
     * under `getExternalFilesDir(null)/trash` — because a bin stored here would be silently truncated
     * from two days to six hours, and the user would never learn why their restore list emptied.
     * `TrashRoots` asserts the two paths cannot overlap (`check` against this exact directory) so a
     * future change to either cannot silently re-introduce the collision.
     */
    private fun tempDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "movies")
        return dir.apply { mkdirs() }
    }

    private companion object {
        const val VIDEO_MIME = "video/mp4"
        const val APP_SUBDIR = "PhoneCleaner"

        /** Long enough that it can never catch a transcode still running in another process. */
        const val ABANDONED_AFTER_MILLIS = 6L * 60L * 60L * 1_000L

        /** Generous for a single-file index; the point is that it is finite. API 28 only. */
        const val SCAN_TIMEOUT_MILLIS = 30_000L
    }
}
