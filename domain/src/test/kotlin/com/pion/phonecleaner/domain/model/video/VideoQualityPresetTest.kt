package com.pion.phonecleaner.domain.model.video

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQualityPresetTest {

    @Test
    fun `bitrateBps returns the column for the requested codec`() {
        assertEquals(2_500_000, VideoQualityPreset.Balanced.bitrateBps(VideoCodecOption.H264))
        assertEquals(1_500_000, VideoQualityPreset.Balanced.bitrateBps(VideoCodecOption.Hevc))
    }

    /** Choosing HEVC must produce a smaller file, not a better-looking one of the same size. */
    @Test
    fun `HEVC is lower than H264 at every preset`() {
        VideoQualityPreset.entries.forEach { preset ->
            assertTrue(
                "preset=$preset",
                preset.bitrateBps(VideoCodecOption.Hevc) < preset.bitrateBps(VideoCodecOption.H264),
            )
        }
    }

    /** A `0` here would be handed to `VideoEncoderSettings.setBitrate` and fail as a broken export. */
    @Test
    fun `no preset carries a zero or negative bitrate for either codec`() {
        VideoQualityPreset.entries.forEach { preset ->
            assertTrue("preset=$preset h264", preset.h264BitrateBps > 0)
            assertTrue("preset=$preset hevc", preset.hevcBitrateBps > 0)
        }
    }
}
