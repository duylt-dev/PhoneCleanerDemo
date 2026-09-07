package com.pion.phonecleaner.domain.policy

import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The never-enlarge rule — the most important test in the feature (`plans/260907-0142-video-
 * compression/phase-09-unit-tests.md` key insight 1). Media3's `Presentation.configure()` has no
 * upscale guard of its own (`VideoScaling`'s own KDoc), so [VideoScaling.targetShortSideOrNull]
 * returning `null` IS the guard — this suite is the only thing standing between a refactor and a
 * 480p clip coming back bigger than it went in.
 */
class VideoScalingTest {

    @Test
    fun `a landscape source above the preset is scaled to the preset's short side`() {
        assertEquals(720, VideoScaling.targetShortSideOrNull(1920, 1080, VideoQualityPreset.Balanced))
    }

    /** The SHORT side, not the height — a portrait clip's short side is its width. */
    @Test
    fun `a portrait source above the preset is scaled by its short side, not its height`() {
        assertEquals(720, VideoScaling.targetShortSideOrNull(1080, 1920, VideoQualityPreset.Balanced))
    }

    @Test
    fun `a source already smaller than the preset is not enlarged`() {
        assertNull(VideoScaling.targetShortSideOrNull(640, 480, VideoQualityPreset.Quality))
    }

    /** Equal is not smaller — a source exactly at the preset is left alone too. */
    @Test
    fun `a source exactly at the preset is not touched`() {
        assertNull(VideoScaling.targetShortSideOrNull(1280, 720, VideoQualityPreset.Balanced))
    }

    @Test
    fun `unknown dimensions never guess a scale`() {
        assertNull(VideoScaling.targetShortSideOrNull(0, 0, VideoQualityPreset.Balanced))
    }

    @Test
    fun `one unknown dimension is still refused, not treated as zero-width`() {
        assertNull(VideoScaling.targetShortSideOrNull(1920, 0, VideoQualityPreset.Balanced))
    }

    /**
     * The rule is not one the biggest preset gets to break: a 640x480 source's short side (480) is at
     * or below EVERY preset's short side (480 / 720 / 1080), so all three must refuse to enlarge it —
     * asserting only `Balanced` would prove nothing about `Saver` or `Quality`.
     */
    @Test
    fun `never enlarge holds at every preset, not just the one it was written for`() {
        VideoQualityPreset.entries.forEach { preset ->
            assertNull(
                "preset=$preset must not enlarge a 640x480 source",
                VideoScaling.targetShortSideOrNull(640, 480, preset),
            )
        }
    }

    // No test for "the rule ignores the codec": `targetShortSideOrNull(width, height, preset)` has no
    // codec parameter at all, so there is nothing a runtime assertion could check — the guarantee is
    // structural, enforced by the signature every caller already compiles against. Resolution and
    // bitrate are independent levers on purpose (VideoScaling's own KDoc, VideoQualityPreset.bitrateBps's
    // own KDoc): only resolution is ever refused here.
}
