package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressionEstimate
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.policy.VideoSizeEstimate

/**
 * What the picker's estimate panel shows, folded over the selection
 * (`phase-03-domain-video-compression.md` step 9).
 *
 * Pure — injects nothing. The codec is a parameter for one visible reason: switching the codec chip
 * must move the figure on screen, and it cannot if the estimate never sees it.
 *
 * Registered `factoryOf` like every other use case even though it has no dependencies — `LLM.md`
 * §6.5 requires registration in the same change, and a ViewModel taking a use case type is what
 * keeps the call site uniform.
 */
class EstimateVideoCompressionUseCase {
    operator fun invoke(
        selection: List<VideoCandidate>,
        preset: VideoQualityPreset,
        codec: VideoCodecOption,
    ): VideoCompressionEstimate {
        var beforeBytes = 0L
        var estimatedAfterBytes = 0L
        var measuredCount = 0
        var unmeasuredCount = 0

        for (candidate in selection) {
            val estimated = VideoSizeEstimate.estimatedBytesOrNull(
                sourceBytes = candidate.sizeBytes,
                durationMs = candidate.durationMs,
                preset = preset,
                codec = codec,
            )
            if (estimated == null) {
                unmeasuredCount++
            } else {
                beforeBytes += candidate.sizeBytes
                estimatedAfterBytes += estimated
                measuredCount++
            }
        }

        return VideoCompressionEstimate(
            beforeBytes = beforeBytes,
            estimatedAfterBytes = estimatedAfterBytes,
            measuredCount = measuredCount,
            unmeasuredCount = unmeasuredCount,
        )
    }
}
