package com.pion.phonecleaner.data.files

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.repository.VideoEncoderCapabilities
import kotlinx.coroutines.withContext

/**
 * The hardware-encoder-only probe behind [VideoEncoderCapabilities]
 * (`phase-04-data-media3-engine.md` step 3b, key insight 8b).
 *
 * **"Has an HEVC encoder" is the wrong question, and asking it ships the feature broken.** The test
 * device (Samsung SM-A165F, MediaTek, API 36) declares **two** HEVC encoders:
 * `c2.mtk.hevc.encoder` (hardware) and `c2.android.hevc.encoder` — Google's **software** encoder,
 * which the `c2.android.*`/`OMX.google.*` naming convention marks and which is present on
 * essentially every Android device. A probe written the obvious way — "does `MediaCodecList` hold an
 * encoder for `video/hevc`?" — therefore answers **true almost everywhere**, including on devices
 * with no hardware HEVC encoder at all. On those the option is offered, the user takes it, and a
 * 1080p transcode grinds at a fraction of real time: minutes of video into tens of minutes of work.
 * That is worse than not offering HEVC, so [isHardware] is the whole point of this class, not an
 * optional tightening of it.
 *
 * `android.media.MediaCodecList` is the **platform**, not the media3 library. Using it here does not
 * widen the two-file media3-import rule of `LLM.md` §3.6/§4 — this file imports none of it.
 */
internal class MediaCodecVideoEncoderCapabilities(
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : VideoEncoderCapabilities {

    /**
     * H.264 is `true` by decision, never by probe (D6, [VideoEncoderCapabilities]'s own KDoc): it is
     * the guaranteed fallback and a `false` here would leave the picker with no codec at all. Only
     * HEVC is asked about, because it is the only codec this device might lack real hardware for.
     */
    override suspend fun isSupported(codec: VideoCodecOption): Boolean = when (codec) {
        VideoCodecOption.H264 -> true
        VideoCodecOption.Hevc -> withContext(dispatchers.default) {
            runCatching { hasHardwareEncoder(codec.mimeType) }
                .onFailure { log.e(it) { "Codec list unreadable while probing ${codec.mimeType}" } }
                .getOrDefault(false)
        }
    }

    /**
     * Never throws (`VideoEncoderCapabilities`'s own contract): "we could not read the codec list"
     * and "there is no encoder" reach the identical screen — the option disabled with a reason — so a
     * caller has no branch that would tell the two apart. [isSupported] is what turns a thrown
     * [Throwable] into that same `false`.
     */
    private fun hasHardwareEncoder(mimeType: String): Boolean =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
            info.isEncoder &&
                !info.name.endsWith(SECURE_SUFFIX, ignoreCase = true) &&
                info.supportedTypes.any { it.equals(mimeType, ignoreCase = true) } &&
                info.isHardware()
        }

    /**
     * **Two branches because `minSdk` is 28.** [MediaCodecInfo.isHardwareAccelerated] is the real
     * answer and arrived in API 29 (Q); below it the method does not exist, and the fallback is the
     * documented AOSP naming convention — `OMX.google.*` and `c2.android.*` are software, anything
     * else is treated as hardware. **This is a heuristic forced by `minSdk 28`, not a simplification
     * of the API-29 branch** — deleting it in favour of "just call `isHardwareAccelerated`" reports
     * *no* hardware HEVC encoder on every API 28 device, silently disabling HEVC everywhere on the
     * floor SDK.
     */
    private fun MediaCodecInfo.isHardware(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAccelerated
        } else {
            SOFTWARE_NAME_PREFIXES.none { name.startsWith(it, ignoreCase = true) }
        }

    private companion object {
        /** DRM decode paths, not general encode targets — never a candidate for this feature. */
        const val SECURE_SUFFIX = ".secure"

        /** AOSP's own naming convention for its software codecs, relied on only below API 29. */
        val SOFTWARE_NAME_PREFIXES = listOf("OMX.google.", "c2.android.")
    }
}
