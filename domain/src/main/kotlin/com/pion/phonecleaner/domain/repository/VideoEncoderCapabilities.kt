package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.video.VideoCodecOption

/**
 * The platform question `:domain` may ask but not answer (`phase-03-domain-video-compression.md`
 * step 8, key insight 9, D6).
 *
 * **`Boolean`, not `AppResult<Boolean>`.** Every other port here returns `AppResult` because a
 * caller can render a failure differently from a result; this one cannot. "We could not read the
 * codec list" and "there is no encoder" lead to the identical screen — the option disabled with a
 * reason — so a failure arm would be a branch no caller could take. The implementation therefore
 * never throws across the boundary either (§4's rule, honoured by swallowing into `false`, not by
 * an `AppResult`).
 *
 * **`suspend`, because the answer comes from a platform scan** (`MediaCodecList`, Phase 04) and the
 * dispatcher choice belongs inside the implementation, never at the call site (`LLM.md` §6.5).
 *
 * **`isSupported(H264)` is always `true`, by decision and not by probe (D6).** H.264 is the
 * guaranteed default and the fallback everything else rests on; a `false` there would leave the
 * screen with no codec at all rather than with a useful refusal, and no state models that. So the
 * device probe decides **HEVC only** (Phase 04), and every fake must answer `true` for H.264 too —
 * a fake that does not is testing a device this app cannot serve.
 *
 * **"Supported" means a *hardware* encoder**, and the reason is in Phase 04: the AOSP software HEVC
 * encoder is present on essentially every device, so a naive probe says yes everywhere and the user
 * gets a 1080p transcode running at a fraction of real time. `:domain` states the question; Phase 04
 * states what counts as an answer.
 *
 * The one-method shape is deliberate. Anything more (a list of codecs, a resolution query) is a
 * second question nobody is asking yet.
 */
interface VideoEncoderCapabilities {
    /**
     * Whether this device can encode [codec]. Asked once, before the picker renders its chips.
     */
    suspend fun isSupported(codec: VideoCodecOption): Boolean
}
