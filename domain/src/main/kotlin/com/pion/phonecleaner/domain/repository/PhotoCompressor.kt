package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.photo.CompressStep
import com.pion.phonecleaner.domain.model.photo.CompressionEstimate
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.coroutines.flow.Flow

/**
 * The re-encoder (`docs/screens/13-photo-and-media.md` §0.3, §4.2).
 *
 * ### The two constants live here, not in `:data`
 *
 * §4.2 puts `QUALITY` and `MAX_EDGE_PX` on `BitmapPhotoCompressor` and then has the ViewModel pass
 * them — and a `:feature` module cannot see `:data` (`LLM.md` §2), so it could only pass literals.
 * They are default arguments on the port instead: one named value each, visible to the caller, and
 * still one place to change. That is the same rule §4.2 was reaching for.
 */
interface PhotoCompressor {

    /**
     * Sequential, one [CompressStep] per photo as it lands. A failure is a step with `failed = true`,
     * never a silent skip inside a `runCatching` (§4.5).
     */
    fun compress(
        ids: List<PhotoId>,
        quality: Int = DEFAULT_QUALITY,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
    ): Flow<CompressStep>

    /**
     * Re-encodes up to [sampleSize] photos **in memory** and reports what they actually came to.
     *
     * §3.2 requires the intro panel's figure to be sampled "through the real encoder" and §0.3's
     * interface has no method that can do it without writing. Nothing is written here; that is the
     * whole point of a separate method rather than reusing [compress].
     */
    suspend fun estimate(
        ids: List<PhotoId>,
        sampleSize: Int = DEFAULT_ESTIMATE_SAMPLE,
        quality: Int = DEFAULT_QUALITY,
        maxEdgePx: Int = DEFAULT_MAX_EDGE_PX,
    ): AppResult<CompressionEstimate>

    companion object {
        /**
         * 78, the competitor's JPEG quality — and the one number of its encoder that is not a defect:
         * `(int)(f10 * 100)` uses the **un-coerced** parameter, so 78 is what it always was
         * (`docs/reverse-engineering/13-photo-and-media.md` §4.3).
         */
        const val DEFAULT_QUALITY: Int = 78

        /**
         * The longest edge a re-encoded photo may keep. The scale is `min(1f, maxEdge / longestEdge)`
         * — it **never enlarges the shrink for a small photo**, which `min(0.78, w/1024, h/1024)`
         * does: that scales a 400 px thumbnail by ~0.39, harder than a 4 000 px photo (§4.5).
         */
        const val DEFAULT_MAX_EDGE_PX: Int = 1024

        /** How many photos the intro panel's estimate re-encodes before it reports a figure. */
        const val DEFAULT_ESTIMATE_SAMPLE: Int = 3
    }
}
