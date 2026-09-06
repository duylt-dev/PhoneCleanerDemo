package com.pion.phonecleaner.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.pion.phonecleaner.domain.repository.BlurDetector

/**
 * Sharpness as the **variance of the 3x3 Laplacian response** over an 8-bit grey image — the
 * standard estimator. A focused photo has strong second derivatives at its edges and therefore a
 * wide spread of responses; a blurred one has almost none and the variance collapses toward zero.
 *
 * Nothing in the competitor's APK corresponds to this class:
 * `docs/reverse-engineering/13-photo-and-media.md:544` records that **no sharpness, resolution or
 * size heuristic exists anywhere in it**. Every other engine in this cluster is written against an
 * observed defect; this one has no such source, so the decisions below are argued from the estimator
 * rather than cited to a line. The arithmetic itself lives in [LaplacianVariance] so that a test can
 * reach it without a `Bitmap`.
 *
 * ### The two decisions that make the score comparable between photos
 *
 *  1. **A fixed working size, reached only by shrinking.** Laplacian variance scales with
 *     resolution, so the same photo measured at 4 000 px and at 500 px yields two different numbers;
 *     every photo is therefore resampled so its longest edge is exactly
 *     [LaplacianVariance.WORK_EDGE]. A photo already **smaller** than that is not enlarged to fit —
 *     it is refused. [LaplacianVariance.MIN_SCORABLE_EDGE] carries the measurements behind that
 *     refusal; the short version is that bilinear enlargement destroys the high-frequency content
 *     this estimator reads, so enlarging made every small sharp image score as very blurry, on a
 *     screen where that means *pre-ticked for deletion*.
 *  2. **A failed or impossible measurement is `null`, never `0.0`.** Zero is the blurriest possible
 *     score. See [BlurDetector.score].
 *
 * The decode goes through [PhotoBitmaps.decodeSampled], so **no full-resolution bitmap is ever
 * created** — the same guarantee the hasher gets, and the reason the competitor's photo scan
 * decoding 5 000 full-size bitmaps at once is not reproduced here.
 *
 * **This class does not choose its own dispatcher.** `DefaultBlurryPhotoScanner` holds the
 * concurrency budget for the whole scan, because it is the only object that knows how many photos
 * are in flight; a `withContext` here would hop off that budget and undo it.
 */
internal class LaplacianBlurDetector(
    private val context: Context,
) : BlurDetector {

    override suspend fun score(uri: String): Double? {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return null
        val decoded = PhotoBitmaps.decodeSampled(context, parsed, LaplacianVariance.WORK_EDGE)
            ?: return null
        val scaled = shrinkToWorkEdge(decoded)
        if (scaled == null) {
            decoded.recycle()
            return null
        }
        return try {
            LaplacianVariance.of(greyOf(scaled), scaled.width, scaled.height)
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    /**
     * Longest edge down to [LaplacianVariance.WORK_EDGE], aspect preserved.
     *
     * `null` when the source is **smaller** than the working edge — see
     * [LaplacianVariance.MIN_SCORABLE_EDGE], which is the whole reason this function shrinks only.
     * `null` again when the shrink would leave an edge with no interior pixel to convolve, which an
     * extreme-aspect panorama can do.
     */
    private fun shrinkToWorkEdge(source: Bitmap): Bitmap? {
        val longest = maxOf(source.width, source.height)
        if (longest < LaplacianVariance.MIN_SCORABLE_EDGE) return null
        if (longest == LaplacianVariance.WORK_EDGE) return source
        val factor = LaplacianVariance.WORK_EDGE.toDouble() / longest
        val width = (source.width * factor).toInt().coerceAtLeast(1)
        val height = (source.height * factor).toInt().coerceAtLeast(1)
        if (width < LaplacianVariance.MIN_MEASURABLE_EDGE ||
            height < LaplacianVariance.MIN_MEASURABLE_EDGE
        ) {
            return null
        }
        if (width == source.width && height == source.height) return source
        return runCatching { Bitmap.createScaledBitmap(source, width, height, true) }.getOrNull()
    }

    /** `(R*38 + G*75 + B*15) >> 7` — the same luma weights `DctPerceptualHasher` uses. */
    private fun greyOf(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            pixels[index] = ((red * 38) + (green * 75) + (blue * 15)) shr 7
        }
        return pixels
    }
}
