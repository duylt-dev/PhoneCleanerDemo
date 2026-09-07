package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressOutcome
import com.pion.phonecleaner.domain.model.video.VideoCompressProgress
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.repository.CompressedVideoLedger
import com.pion.phonecleaner.domain.repository.VideoCompressor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * The run itself (`phase-03-domain-video-compression.md` step 9). Mirrors `CompressPhotosUseCase`.
 *
 * Recorded **as each step lands**, not in a batch at the end — a run cancelled halfway has still
 * produced real files, and those are exactly the rows the next scan must not offer back. The
 * **output** id is recorded, not the source's: after the delete step the source row is gone.
 *
 * The [VideoCompressProgress.Working] arm falls through untouched: a percentage is not an outcome,
 * and recording on it would write the same id fifty times per video.
 */
class CompressVideosUseCase(
    private val compressor: VideoCompressor,
    private val ledger: CompressedVideoLedger,
) {
    operator fun invoke(
        ids: List<String>,
        preset: VideoQualityPreset,
        codec: VideoCodecOption,
    ): Flow<VideoCompressProgress> = compressor.compress(ids, preset, codec)
        .onEach { progress ->
            val step = (progress as? VideoCompressProgress.Finished)?.step ?: return@onEach
            val outputId = step.outputId
            if (step.outcome == VideoCompressOutcome.Compressed && outputId != null) {
                ledger.record(outputId)
            }
        }
}
