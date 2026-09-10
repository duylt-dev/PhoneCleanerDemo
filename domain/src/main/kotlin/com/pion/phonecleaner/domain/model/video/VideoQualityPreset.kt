package com.pion.phonecleaner.domain.model.video

import kotlinx.serialization.Serializable

/**
 * The three resolution/bitrate tiers the picker offers (`phase-03-domain-video-compression.md`
 * step 1, D5).
 *
 * **[shortSidePx], not a height.** Phone video is portrait, and `Presentation.createForShortSide`
 * is the only Media3 factory that preserves that (`Presentation.java:195`,
 * `preservePortraitWhenApplicable = true`, verified in
 * `reports/researcher-01-media3-transformer-api.md` §4). `createForHeight(720)` on a 1080x1920
 * portrait clip produces 405x720 — a crop-shaped mistake.
 *
 * **These six numbers are ours.** No source states them, exactly as
 * [com.pion.phonecleaner.domain.usecase.LoadCompressiblePhotosUseCase.MIN_COMPRESSIBLE_BYTES] is
 * ours. The H.264 column is the conventional target for its resolution; the HEVC column is ~0.6x of
 * it. They live on the enum so a change is one edit, and they are named so the picker chips render
 * labels, never numbers the user must interpret.
 *
 * **Why HEVC gets a lower number, not the same one.** At an equal bitrate HEVC returns a
 * *better-looking* file of the same size. This feature exists to make the file smaller, so choosing
 * HEVC must produce a smaller file — the quality gain is spent, deliberately, on bytes.
 *
 * **[bitrateBps] is the only accessor.** A `when (codec)` written at a call site is a second pairing
 * of codec to number, free to drift from this one; the exhaustive `when` here breaks the build the
 * day a third codec is added, and a call-site branch does not.
 *
 * **Bitrate and resolution are independent levers, and only resolution is ever refused.** The
 * never-enlarge rule lives in [com.pion.phonecleaner.domain.policy.VideoScaling] and touches nothing
 * here: a source already below the preset keeps its resolution and is still re-encoded at the
 * preset's bitrate for its codec.
 *
 * **This deliberately differs from `PhotoCompressor.DEFAULT_QUALITY`/`DEFAULT_MAX_EDGE_PX`**, which
 * are single fixed constants. A photo re-encode is instant and reversible in practice; a video
 * transcode is minutes and destroys the original if the user then deletes it, so the trade-off is
 * the user's.
 *
 * The labels are `@StringRes` in `:feature:files` — `:domain` has no `Resources` (§2).
 *
 * `@Serializable` because both this enum and [VideoCodecOption] are route arguments on
 * `Route.VideoCompressRun` (Phase 08, `LLM.md` §7.2). `:domain` already applies
 * `kotlin-serialization`, so this costs no build change.
 */
@Serializable
enum class VideoQualityPreset(
    val shortSidePx: Int,
    val h264BitrateBps: Int,
    val hevcBitrateBps: Int,
) {
    Saver(480, 1_000_000, 600_000),
    Balanced(720, 2_500_000, 1_500_000),
    Quality(1080, 5_000_000, 3_000_000),
    ;

    /** The bitrate to request for [codec] at this preset. The **only** way a call site gets one. */
    fun bitrateBps(codec: VideoCodecOption): Int = when (codec) {
        VideoCodecOption.H264 -> h264BitrateBps
        VideoCodecOption.Hevc -> hevcBitrateBps
    }

    companion object {
        val Default: VideoQualityPreset = Balanced
    }
}
