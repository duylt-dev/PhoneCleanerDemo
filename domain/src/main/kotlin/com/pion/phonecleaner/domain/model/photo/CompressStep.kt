package com.pion.phonecleaner.domain.model.photo

/**
 * One photo's re-encode, reported as it finishes (`docs/screens/13-photo-and-media.md` §0.3).
 *
 * [beforeBytes] and [afterBytes] are **measured**, both of them. The competitor reports
 * `0.6 × selectedBytes` — a constant multiplied by the selection — as the saving it shows the user
 * (§4.5), while its own intro panel claims "up to about 40%"; the two numbers cannot both be right
 * and neither is measured.
 *
 * [failed] is a first-class outcome, not a silent `runCatching` skip: `nd/b.h` wraps the whole body
 * so a photo that could not be written is indistinguishable from one that was (§4.5).
 */
data class CompressStep(
    val index: Int,
    val total: Int,
    val id: PhotoId,
    val beforeBytes: Long,
    val afterBytes: Long,
    val failed: Boolean,
) {
    /**
     * Never negative. A re-encode that would grow the file is skipped rather than written, so the
     * step reports `afterBytes == beforeBytes` and contributes nothing — it does not subtract.
     */
    val savedBytes: Long get() = (beforeBytes - afterBytes).coerceAtLeast(0L)
}
