package com.pion.phonecleaner.domain.model.video

import kotlinx.serialization.Serializable

/**
 * The user's second choice, after the preset (`phase-03-domain-video-compression.md` step 1b, D6).
 *
 * **The MIME string lives on the enum, not in a `when` inside the `:data` engine.** §2 forbids
 * `:domain` from naming an **Android type**, and a MIME type is an IANA string —
 * `ScannedFile.mimeType` already carries one straight out of `MediaStore`, and
 * `androidx.media3.common.MimeTypes.VIDEO_H264` *is* the literal `"video/avc"` — so putting it here
 * keeps **one** mapping that both the engine and the capability probe read, where a `when` in
 * `:data` would be two mappings free to drift apart. `androidx.media3.common.MimeTypes` itself is
 * still never imported by `:domain`.
 *
 * - **H.264 is the default and is always offered.** It is the encoder the whole feature falls back
 *   to; there is no second fallback, which is why the picker never renders it disabled.
 * - HEVC is offered on every device and **disabled with a reason** where the probe says no
 *   ([com.pion.phonecleaner.domain.repository.VideoEncoderCapabilities]). Never silently absent —
 *   an option that vanishes teaches the user nothing, and the reason is what stops the support
 *   question.
 * - Two constants, exactly. AV1 encode is not on the table at `minSdk 28`; adding a third constant
 *   breaks the exhaustive `when` in `VideoQualityPreset.bitrateBps`, which is the intended alarm.
 */
@Serializable
enum class VideoCodecOption(val mimeType: String) {
    H264("video/avc"),
    Hevc("video/hevc"),
    ;

    companion object {
        val Default: VideoCodecOption = H264
    }
}
