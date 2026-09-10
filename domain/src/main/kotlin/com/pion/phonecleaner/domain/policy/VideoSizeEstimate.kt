package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset

/**
 * The estimate's arithmetic, and the required-free-space arithmetic it feeds
 * (`phase-03-domain-video-compression.md` step 7, key insight 6).
 *
 * **It is still an estimate and the screen must say so.** Nothing here is measured output: the
 * measured inputs are the row's `SIZE` and `DURATION` from MediaStore, and the arithmetic over them
 * — bitrate x duration — is ours. Contrast the photo side's `CompressionEstimate`, whose figures
 * come from a real in-memory re-encode; that is not possible for video in a tap.
 */
object VideoSizeEstimate {
    /**
     * `null` when [durationMs] or [sourceBytes] is not usable — never a `0` that reads as a valid
     * measurement.
     *
     * **Takes [codec], because the bitrate depends on it (D5).** Switching the picker from H.264 to
     * HEVC at the same preset must move the figure on screen; an estimate that ignored the codec
     * would quietly promise the H.264 size for an HEVC run.
     *
     * `minOf(sourceBytes, …)` is not cosmetic: the engine **refuses to publish an output that is not
     * smaller** (Phase 04), so an estimate above the source would promise something the engine will
     * never produce.
     */
    fun estimatedBytesOrNull(
        sourceBytes: Long,
        durationMs: Long,
        preset: VideoQualityPreset,
        codec: VideoCodecOption,
    ): Long? {
        if (durationMs <= 0L || sourceBytes <= 0L) return null
        val bitsPerSecond = preset.bitrateBps(codec).toLong() + AUDIO_ALLOWANCE_BPS
        val estimated = bitsPerSecond * durationMs / (8L * 1_000L)
        return minOf(sourceBytes, estimated)
    }

    /**
     * `Σ outputs + max(output)`. The run is sequential and **the originals are not deleted until the
     * user confirms (D4)**, so every output coexists with every original, and during one publish the
     * in-flight video exists twice (temp file plus the copy being written). The transient peak is
     * one output, not all of them — that extra `max` term is what covers it.
     */
    fun requiredFreeBytes(estimatedOutputs: List<Long>): Long {
        if (estimatedOutputs.isEmpty()) return 0L
        return estimatedOutputs.sum() + estimatedOutputs.max()
    }

    /**
     * An **allowance, not a measurement**: audio is transmuxed unchanged and we do not know the
     * source track's rate without opening the file. Named so it is visibly an assumption.
     */
    const val AUDIO_ALLOWANCE_BPS: Long = 128_000L

    /** Headroom the space check refuses to consume. Ours; no source states it. */
    const val FREE_SPACE_FLOOR_BYTES: Long = 200L * 1024L * 1024L
}
