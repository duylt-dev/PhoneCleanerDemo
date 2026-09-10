package com.pion.phonecleaner.domain.model.video

/**
 * How one video's re-encode landed (`phase-03-domain-video-compression.md` step 3).
 *
 * **Three outcomes, not two.** The photo `CompressStep` folds "the re-encode was not smaller" into
 * `afterBytes == beforeBytes, failed = false`, which the photo screen renders as nothing at all.
 * For video that silence is unacceptable: the user waited two minutes. [NotSmaller] is a
 * first-class, countable, renderable outcome. [Failed] stays what it is on the photo side: a
 * reported failure, never a silent skip in a `runCatching`, never a `0` that reads as a
 * measurement.
 */
enum class VideoCompressOutcome { Compressed, NotSmaller, Failed }

/**
 * One video's finished re-encode, reported as it lands.
 *
 * [beforeBytes] and [afterBytes] are **measured**, both of them, never a fraction of the input —
 * see [VideoCompressionEstimate] for the arithmetic-only counterpart taken *before* a run.
 */
data class VideoCompressStep(
    val index: Int,
    val total: Int,
    val id: String,
    val beforeBytes: Long,
    val afterBytes: Long,
    val outcome: VideoCompressOutcome,
    /** The row the engine created, or `null` for [VideoCompressOutcome.NotSmaller] and `Failed`. */
    val outputId: String? = null,
) {
    /** Never negative. Zero unless [outcome] is [VideoCompressOutcome.Compressed]. */
    val savedBytes: Long
        get() = if (outcome == VideoCompressOutcome.Compressed) {
            (beforeBytes - afterBytes).coerceAtLeast(0L)
        } else {
            0L
        }

    val failed: Boolean get() = outcome == VideoCompressOutcome.Failed
}
