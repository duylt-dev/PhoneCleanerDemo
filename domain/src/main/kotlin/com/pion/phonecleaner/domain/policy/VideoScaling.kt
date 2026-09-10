package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.video.VideoQualityPreset

/**
 * The never-enlarge rule (`phase-03-domain-video-compression.md` step 6, key insight 1).
 *
 * **`Presentation.configure()` assigns the requested size unconditionally**
 * (`Presentation.java:308-322`, read at tag 1.11.0, verified in
 * `reports/researcher-01-media3-transformer-api.md` §4) — **there is no upscale guard in the
 * library.** A 480p source under the 1080p preset would be re-encoded *upward* — minutes of CPU to
 * produce a *larger* file than the original. That is the exact bug the blurry-photo feature already
 * paid for with an upscale-to-normalise mistake; here the cost is worse because the output is bigger
 * than the input, not merely wrong.
 *
 * `null` means *omit the effect entirely* — `Presentation` has no no-op form of its own, unlike the
 * photo compressor's `min(1f, maxEdgePx / longestEdge)`, which collapses to a scale of `1.0` for a
 * small photo. `targetShortSideOrNull` returning a nullable `Int` rather than a clamped one is that
 * missing no-op, expressed the only way this policy can: by telling the caller not to add a
 * `Presentation` to the effect list at all.
 *
 * Unknown dimensions (`<= 0`) also return `null`: we do not guess a source's size, and re-encoding
 * at the preset bitrate without scaling is a correct, safe outcome.
 *
 * **Resolution and bitrate are INDEPENDENT levers, and only resolution is ever refused here.** A
 * `null` from this function means *keep the resolution*, never *skip the video*: the bitrate lever
 * is untouched by this rule and is chosen per codec by
 * [VideoQualityPreset.bitrateBps]. A 480p clip recorded at 8 Mbps still shrinks a lot at the 480p
 * preset's 1 Mbps, and more again on HEVC, whose preset bitrate is lower still — omitting the scale
 * does not make the tool useless on a small video. This function takes no codec, and does not need
 * one.
 */
object VideoScaling {
    /**
     * The short side to render at, or `null` when the source is already at or below the preset and
     * must therefore be left alone.
     */
    fun targetShortSideOrNull(width: Int, height: Int, preset: VideoQualityPreset): Int? {
        if (width <= 0 || height <= 0) return null
        val sourceShortSide = minOf(width, height)
        return if (sourceShortSide <= preset.shortSidePx) null else preset.shortSidePx
    }
}
