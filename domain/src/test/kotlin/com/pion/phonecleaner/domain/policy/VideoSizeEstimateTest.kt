package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoSizeEstimateTest {

    @Test
    fun `a normal clip estimates bitrate times duration plus the audio allowance`() {
        val estimate = VideoSizeEstimate.estimatedBytesOrNull(
            sourceBytes = 200L * 1024L * 1024L,
            durationMs = 60_000L,
            preset = VideoQualityPreset.Balanced,
            codec = VideoCodecOption.H264,
        )

        val expected = (
            VideoQualityPreset.Balanced.bitrateBps(VideoCodecOption.H264) +
                VideoSizeEstimate.AUDIO_ALLOWANCE_BPS
            ) * 60_000L / (8L * 1_000L)
        assertEquals(expected, estimate)
    }

    /** D5 — without this, choosing HEVC would silently promise the H.264 size. */
    @Test
    fun `the estimate varies by codec — HEVC is strictly smaller than H264`() {
        val h264 = VideoSizeEstimate.estimatedBytesOrNull(
            200L * 1024L * 1024L,
            60_000L,
            VideoQualityPreset.Balanced,
            VideoCodecOption.H264,
        )
        val hevc = VideoSizeEstimate.estimatedBytesOrNull(
            200L * 1024L * 1024L,
            60_000L,
            VideoQualityPreset.Balanced,
            VideoCodecOption.Hevc,
        )
        assertTrue(requireNotNull(hevc) < requireNotNull(h264))
    }

    /** A single preset passing proves nothing about the other two rows of the table. */
    @Test
    fun `HEVC is smaller than H264 at every preset`() {
        VideoQualityPreset.entries.forEach { preset ->
            val h264 = VideoSizeEstimate.estimatedBytesOrNull(
                200L * 1024L * 1024L,
                60_000L,
                preset,
                VideoCodecOption.H264,
            )
            val hevc = VideoSizeEstimate.estimatedBytesOrNull(
                200L * 1024L * 1024L,
                60_000L,
                preset,
                VideoCodecOption.Hevc,
            )
            assertTrue(
                "preset=$preset: HEVC ($hevc) must be smaller than H264 ($h264)",
                requireNotNull(hevc) < requireNotNull(h264),
            )
        }
    }

    /** Never a `0` that reads as a valid measurement. */
    @Test
    fun `zero duration is refused, never a 0 that reads as a measurement`() {
        assertNull(
            VideoSizeEstimate.estimatedBytesOrNull(
                200L * 1024L * 1024L,
                0L,
                VideoQualityPreset.Balanced,
                VideoCodecOption.H264,
            ),
        )
    }

    @Test
    fun `zero source size is refused`() {
        assertNull(
            VideoSizeEstimate.estimatedBytesOrNull(0L, 60_000L, VideoQualityPreset.Balanced, VideoCodecOption.H264),
        )
    }

    @Test
    fun `negative source size is refused`() {
        assertNull(
            VideoSizeEstimate.estimatedBytesOrNull(-1L, 60_000L, VideoQualityPreset.Balanced, VideoCodecOption.H264),
        )
    }

    /** The engine refuses to publish an output that is not smaller — an estimate above the source lies. */
    @Test
    fun `an estimate that would exceed the source is clamped to the source size`() {
        val estimate = VideoSizeEstimate.estimatedBytesOrNull(
            sourceBytes = 1_000_000L,
            durationMs = 60_000L,
            preset = VideoQualityPreset.Quality,
            codec = VideoCodecOption.H264,
        )
        assertEquals(1_000_000L, estimate)
    }

    @Test
    fun `requiredFreeBytes of nothing selected is zero`() {
        assertEquals(0L, VideoSizeEstimate.requiredFreeBytes(emptyList()))
    }

    /** `sum + max`: the transient peak during one publish is one output, not all of them. */
    @Test
    fun `requiredFreeBytes is the sum plus the single largest output`() {
        assertEquals(90L, VideoSizeEstimate.requiredFreeBytes(listOf(10L, 20L, 30L)))
    }
}
