package com.pion.phonecleaner.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Bitmap decoding for the two engines that need pixels — the hasher and the compressor.
 *
 * **Nothing here decodes a full-resolution bitmap.** The competitor's hash step launches one
 * coroutine per photo with no concurrency limit and each child calls
 * `BitmapFactory.decodeFile(fullPath)`; a 5 000-photo library therefore decodes 5 000 full-size
 * bitmaps at once (`docs/reverse-engineering/13-photo-and-media.md` §4.1 step 2).
 *
 * Everything reads through a `content://` URI, never a `_data` path: in the default storage branch a
 * media row's only readable handle is its URI.
 */
internal object PhotoBitmaps {

    /** Decoded so that the longer edge is at least [minEdgePx], never the whole file. */
    fun decodeSampled(context: Context, uri: Uri, minEdgePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(context, uri) { BitmapFactory.decodeStream(it, null, bounds) } ?: Unit
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(longest, minEdgePx)
        }
        return openStream(context, uri) { BitmapFactory.decodeStream(it, null, options) }
    }

    /** The EXIF rotation, in degrees, so the re-encode keeps the picture the right way up. */
    fun rotationDegrees(context: Context, uri: Uri): Int =
        openStream(context, uri) { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

    /**
     * Rotate, then scale by `min(1f, maxEdgePx / longestEdge)`.
     *
     * **It never enlarges the shrink for a small photo.** `min(0.78, w/1024, h/1024)` scales a 400 px
     * thumbnail by ~0.39 — harder than a 4 000 px photo (§4.5).
     */
    fun transform(source: Bitmap, maxEdgePx: Int, rotationDegrees: Int): Bitmap {
        val longest = maxOf(source.width, source.height).toFloat()
        val scale = if (longest <= 0f) 1f else minOf(1f, maxEdgePx / longest)
        if (scale >= 1f && rotationDegrees == 0) return source
        val matrix = Matrix().apply {
            if (rotationDegrees != 0) postRotate(rotationDegrees.toFloat())
            if (scale < 1f) postScale(scale, scale)
        }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun <T> openStream(context: Context, uri: Uri, read: (java.io.InputStream) -> T): T? =
        runCatching { context.contentResolver.openInputStream(uri)?.use(read) }.getOrNull()

    private fun sampleSize(longestEdge: Int, minEdgePx: Int): Int {
        var sample = 1
        while (minEdgePx > 0 && longestEdge / (sample * 2) >= minEdgePx) sample *= 2
        return sample
    }
}
