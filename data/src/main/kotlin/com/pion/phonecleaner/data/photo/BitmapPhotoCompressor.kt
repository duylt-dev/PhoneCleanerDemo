package com.pion.phonecleaner.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.photo.CompressStep
import com.pion.phonecleaner.domain.model.photo.CompressionEstimate
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.PhotoCompressor
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.ByteArrayOutputStream

/**
 * The re-encoder (`docs/screens/13-photo-and-media.md` §4.2, §4.5).
 *
 * ### What it does differently from `nd/b`, and why each one matters
 *
 * | `nd/b` | here |
 * |---|---|
 * | writes a temp file, `FileUtils.delete(src)`, then `renameTo` — losing all EXIF and, in the privacy engine's case, renaming the photo | writes the new bytes **through the row's own `content://` URI**, so the file keeps its name and its `MediaStore` row |
 * | `min(0.78, w/1024, h/1024)` — a 400 px thumbnail is scaled by 0.39, harder than a 4 000 px photo | `min(1f, maxEdgePx / longestEdge)` (`PhotoBitmaps.transform`) |
 * | a `.png` is "compressed" with a quality argument `Bitmap.compress` ignores | the encoded bytes are compared with the original and **written only when they are smaller** |
 * | a failed photo is skipped silently inside a `runCatching` | `CompressStep.failed`, counted and surfaced |
 * | the saving reported to the user is `0.6 × selectedBytes` | `beforeBytes - afterBytes`, measured per photo |
 *
 * UNKNOWN — **non-GPS EXIF is not carried across.** §4.5 states preserving it as design intent and
 * §8 item 5 records that whether every tag survives a `MediaStore` rewrite on every API level is *not
 * verified against the platform*. Rather than claim it, the rotation is baked into the pixels (so the
 * picture is the right way up with no orientation tag to trust) and nothing else is asserted. Looked
 * for, and not found: any verified tag-preservation procedure in §4 or in
 * `docs/reverse-engineering/13-photo-and-media.md` §4.3.
 *
 * UNKNOWN — **the write-consent round trip is not modelled.** §0.1 says a write to a row we do not
 * own needs `MediaStore.createWriteRequest` consent, and `CompressRunContract` (§4.1) declares no
 * effect, intent or state for it — unlike the delete path, which declares all three. A
 * `SecurityException` therefore lands as `failed = true` on that photo and is reported in the run's
 * `failedCount`, never swallowed. The missing contract is reported, not invented here.
 */
internal class BitmapPhotoCompressor(
    private val context: Context,
    private val photos: PhotoRepository,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : PhotoCompressor {

    override fun compress(ids: List<PhotoId>, quality: Int, maxEdgePx: Int): Flow<CompressStep> = flow {
        val rows = photos.rowsFor(ids)
        if (rows is AppResult.Failure) {
            log.e { "Compression could not read the selection: ${rows.error}" }
            ids.forEachIndexed { index, id ->
                emit(CompressStep(index + 1, ids.size, id, 0L, 0L, failed = true))
            }
            return@flow
        }
        val photos = (rows as AppResult.Success).value
        photos.forEachIndexed { index, photo ->
            emit(step(index + 1, photos.size, photo, quality, maxEdgePx, write = true))
        }
    }.flowOn(dispatchers.io)

    override suspend fun estimate(
        ids: List<PhotoId>,
        sampleSize: Int,
        quality: Int,
        maxEdgePx: Int,
    ): AppResult<CompressionEstimate> {
        val rows = photos.rowsFor(ids.take(sampleSize))
        if (rows is AppResult.Failure) return rows
        val sample = (rows as AppResult.Success).value
        var before = 0L
        var after = 0L
        var sampled = 0
        for (photo in sample) {
            // `write = false`: an estimate re-encodes through the real encoder and stores nothing.
            val step = step(0, sample.size, photo, quality, maxEdgePx, write = false)
            if (step.failed) continue
            before += step.beforeBytes
            after += step.afterBytes
            sampled++
        }
        return CompressionEstimate(beforeBytes = before, afterBytes = after, sampledCount = sampled).asSuccess()
    }

    private fun step(
        index: Int,
        total: Int,
        photo: Photo,
        quality: Int,
        maxEdgePx: Int,
        write: Boolean,
    ): CompressStep {
        val unchanged = CompressStep(index, total, photo.id, photo.sizeBytes, photo.sizeBytes, failed = false)
        val failed = CompressStep(index, total, photo.id, photo.sizeBytes, photo.sizeBytes, failed = true)
        val uri = runCatching { Uri.parse(photo.contentUri) }.getOrNull() ?: return failed
        val encoded = encode(uri, photo.displayName, quality, maxEdgePx) ?: return failed
        // A re-encode that is not smaller is not a saving. This is where a PNG, a small photo and an
        // already-compressed photo all come out — skipped and reported, never written.
        if (encoded.size >= photo.sizeBytes) return unchanged
        if (!write) return CompressStep(index, total, photo.id, photo.sizeBytes, encoded.size.toLong(), false)
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encoded) } ?: return failed
            CompressStep(index, total, photo.id, photo.sizeBytes, encoded.size.toLong(), failed = false)
        } catch (security: SecurityException) {
            // The write-consent gap above. Reported as a failure, not silence.
            log.e(security) { "No write access to ${photo.id.value}" }
            failed
        } catch (io: java.io.IOException) {
            log.e(io) { "Could not rewrite ${photo.id.value}" }
            failed
        }
    }

    private fun encode(uri: Uri, displayName: String, quality: Int, maxEdgePx: Int): ByteArray? {
        val decoded = PhotoBitmaps.decodeSampled(context, uri, maxEdgePx) ?: return null
        val rotation = PhotoBitmaps.rotationDegrees(context, uri)
        val transformed = PhotoBitmaps.transform(decoded, maxEdgePx, rotation)
        return try {
            ByteArrayOutputStream().use { sink ->
                val format = formatOf(displayName)
                if (!transformed.compress(format, quality, sink)) return null
                sink.toByteArray()
            }
        } finally {
            if (transformed !== decoded) transformed.recycle()
            decoded.recycle()
        }
    }

    /** The destination format follows the source's extension, exactly as `nd/b.a` chooses it. */
    private fun formatOf(displayName: String): Bitmap.CompressFormat =
        if (displayName.endsWith(".png", ignoreCase = true)) {
            Bitmap.CompressFormat.PNG
        } else {
            Bitmap.CompressFormat.JPEG
        }
}
