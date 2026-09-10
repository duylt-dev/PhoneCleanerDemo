package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressProgress
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import kotlinx.coroutines.flow.Flow

/**
 * The re-encoder (`phase-03-domain-video-compression.md` step 8).
 *
 * **Sequential, one video at a time, one step per video as it lands.** Not a choice about tidiness:
 * a `Transformer` instance is driven from a single application thread and concurrent instances are
 * not supported (`Transformer.java` throws `IllegalStateException` off-thread, verified in
 * `reports/researcher-01-media3-transformer-api.md` §10). Anyone reaching for `limitedParallelism`
 * here should read `LLM.md` §11 row 12 first — it bounds dispatch slots, not coroutines, and it
 * would bound nothing anyway.
 *
 * **There is no `estimate()` method**, unlike `PhotoCompressor`. An estimate that re-encodes is
 * minutes, not milliseconds; the estimate is
 * [com.pion.phonecleaner.domain.policy.VideoSizeEstimate], pure arithmetic, and it is honest about
 * being one.
 *
 * A cancelled or failed run leaves **no partial file**. That is the implementation's contract,
 * stated on the port because it is the caller's guarantee.
 *
 * **The codec is the caller's, the bitrate is not**: the implementation asks
 * `preset.bitrateBps(codec)` and never carries a number of its own. The default argument exists for
 * the same reason the preset's does — `:feature:files` cannot see `:data` (§2).
 *
 * Passing a codec this device cannot encode is a **caller** error the screen prevents
 * ([VideoEncoderCapabilities]); the engine still reports it as `VideoCompressOutcome.Failed` per
 * item rather than throwing, because an encoder that fails on video 4 of 10 must not end the run.
 */
interface VideoCompressor {
    fun compress(
        ids: List<String>,
        preset: VideoQualityPreset = VideoQualityPreset.Default,
        codec: VideoCodecOption = VideoCodecOption.Default,
    ): Flow<VideoCompressProgress>
}
