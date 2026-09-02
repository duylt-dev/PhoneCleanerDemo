package com.pion.phonecleaner.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.domain.repository.PerceptualHasher
import kotlinx.coroutines.withContext

/**
 * The canonical pHash: 32x32 grey, DCT-II, top-left 8x8, **DC dropped**, bit set above the mean.
 *
 * Three corrections to `od/b0`, each one line, each stated in
 * `docs/screens/13-photo-and-media.md` §1.4:
 *
 *  1. **Grey values, not packed colours.** `b0.e` writes the grey level back as `Color.rgb(g,g,g)`
 *     and `b0.f` then transforms *that packed int*. The transform is linear, so the AC coefficients
 *     come out scaled by `0x010101` and only the DC term absorbs the offset — it works by accident.
 *  2. **The DC coefficient is dropped**, from the bit string and from the mean. `b0.d` keeps it, and
 *     DC dominates the mean, so its bit 0 is effectively always 1: 63 usable bits, not 64.
 *  3. **A failed decode is `null`, not `0L`.** `b0.h` returns `0L` for a null bitmap, so every
 *     unreadable file on the device collapses into one "similar" group, mutual distance zero.
 *
 * The resize also filters (`filter = true`); `b0.l` passes `false`, i.e. nearest-neighbour, which
 * makes the hash of a photo depend on which pixels the sampler happened to land on.
 */
internal class DctPerceptualHasher(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : PerceptualHasher {

    override suspend fun hash(uri: String): Long? = withContext(dispatchers.default) {
        val parsed = runCatching { Uri.parse(uri) }.getOrNull() ?: return@withContext null
        val decoded = PhotoBitmaps.decodeSampled(context, parsed, HASH_EDGE) ?: return@withContext null
        val scaled = runCatching { Bitmap.createScaledBitmap(decoded, HASH_EDGE, HASH_EDGE, true) }
            .getOrNull() ?: return@withContext null
        try {
            hashOf(greyOf(scaled))
        } finally {
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
        }
    }

    override fun distance(a: Long, b: Long): Int = (a xor b).countOneBits()

    /** `(R*38 + G*75 + B*15) >> 7` — the competitor's luma weights, kept as a **grey value**. */
    private fun greyOf(bitmap: Bitmap): DoubleArray {
        val pixels = IntArray(HASH_EDGE * HASH_EDGE)
        bitmap.getPixels(pixels, 0, HASH_EDGE, 0, 0, HASH_EDGE, HASH_EDGE)
        return DoubleArray(pixels.size) { index ->
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            (((red * 38) + (green * 75) + (blue * 15)) shr 7).toDouble()
        }
    }

    private fun hashOf(grey: DoubleArray): Long {
        val transformed = Dct.transform(grey, HASH_EDGE)
        // The top-left 8x8 block, minus the DC term at [0][0].
        val coefficients = DoubleArray(BLOCK * BLOCK)
        for (row in 0 until BLOCK) {
            for (column in 0 until BLOCK) {
                coefficients[row * BLOCK + column] = transformed[row * HASH_EDGE + column]
            }
        }
        val mean = coefficients.drop(1).sum() / (coefficients.size - 1)
        var bits = 0L
        for (index in 1 until coefficients.size) {
            if (coefficients[index] > mean) bits = bits or (1L shl index)
        }
        return bits
    }

    companion object {
        /**
         * Hamming distance **strictly less than 5**, out of 64 — the competitor's threshold, kept
         * (`docs/reverse-engineering/13-photo-and-media.md` §4.1 step 4). One constant, named, so the
         * grouping loop holds no literal (§1.2).
         */
        const val SIMILARITY_MAX_DISTANCE: Int = 5

        private const val HASH_EDGE = 32
        private const val BLOCK = 8
    }
}

/** The 32x32 DCT-II, as `D · P · Dᵀ` over a precomputed basis. */
private object Dct {

    private val basis: DoubleArray = buildBasis(32)

    fun transform(matrix: DoubleArray, size: Int): DoubleArray {
        val intermediate = DoubleArray(size * size)
        for (u in 0 until size) {
            for (x in 0 until size) {
                var sum = 0.0
                for (k in 0 until size) sum += basis[u * size + k] * matrix[k * size + x]
                intermediate[u * size + x] = sum
            }
        }
        val result = DoubleArray(size * size)
        for (u in 0 until size) {
            for (v in 0 until size) {
                var sum = 0.0
                for (k in 0 until size) sum += intermediate[u * size + k] * basis[v * size + k]
                result[u * size + v] = sum
            }
        }
        return result
    }

    private fun buildBasis(size: Int): DoubleArray {
        val values = DoubleArray(size * size)
        val first = Math.sqrt(1.0 / size)
        val rest = Math.sqrt(2.0 / size)
        for (u in 0 until size) {
            val coefficient = if (u == 0) first else rest
            for (x in 0 until size) {
                values[u * size + x] =
                    coefficient * Math.cos(((2 * x + 1) * u * Math.PI) / (2.0 * size))
            }
        }
        return values
    }
}
