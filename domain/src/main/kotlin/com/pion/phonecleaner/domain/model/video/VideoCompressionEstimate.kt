package com.pion.phonecleaner.domain.model.video

/**
 * What the picker shows **before** a run, as arithmetic over two measured inputs
 * (`phase-03-domain-video-compression.md` step 4, key insight 6).
 *
 * This is an **ESTIMATE**, never a measurement: the screen must label it as one.
 * [com.pion.phonecleaner.domain.model.video.VideoCompressStep]'s `beforeBytes`/`afterBytes` are the
 * measured counterpart, taken *after* a run — a photo re-encode can be sampled through the real
 * encoder in under a second (`CompressionEstimate`); a video cannot be sampled that way, so this
 * figure is `bitrate x duration`, clamped to never exceed the source.
 *
 * [unmeasuredCount] exists precisely so an unreadable row cannot be laundered into the total as a
 * zero — the same rule [VideoCompressStep.failed] applies to a completed run applied here to an
 * estimate taken before one.
 */
data class VideoCompressionEstimate(
    val beforeBytes: Long,
    val estimatedAfterBytes: Long,
    /** How many of the selection had a usable duration. */
    val measuredCount: Int,
    /** How many did not, and therefore contributed nothing to either figure above. */
    val unmeasuredCount: Int,
) {
    val estimatedSavedBytes: Long get() = (beforeBytes - estimatedAfterBytes).coerceAtLeast(0L)

    /** `false` when nothing in the selection had a usable duration — the screen shows no figure. */
    val isUsable: Boolean get() = measuredCount > 0
}
