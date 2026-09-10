package com.pion.phonecleaner.feature.files.videocompressrun

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins a real on-device crash (Samsung SM-A165F, 2026-09-07):
 * `ClassCastException: VideoQualityPreset cannot be cast to java.lang.String` the instant the picker
 * navigated to this screen. Type-safe Navigation Compose hands an enum route argument back through
 * `SavedStateHandle` as the DECODED ENUM CONSTANT, not its name, so `get<String>(key)` was an implicit
 * cast that threw on every launch. `readPreset`/`readCodec` now accept both shapes
 * (`VideoCompressRunReducers.kt`); this file is the only thing standing between a future "clean up
 * that when" edit and the same crash coming back.
 *
 * Deliberately NOT mirrored onto the picker's `restoredPreset`/`restoredCodec`
 * (`VideoCompressorReducers.kt`): those read back state the picker itself wrote as a `String`
 * (`savedState[PRESET_KEY] = preset.name`), so `get<String>` there is correct and stays as it is.
 */
class VideoCompressRunReducersTest {

    @Test
    fun `readPreset returns the right preset when the handle holds the enum constant`() {
        val handle = SavedStateHandle(mapOf("preset" to VideoQualityPreset.Quality))

        assertEquals(VideoQualityPreset.Quality, handle.readPreset("preset"))
    }

    /** The process-death path: a value restored from a saved-instance-state bundle. */
    @Test
    fun `readPreset returns the right preset when the handle holds the name string`() {
        val handle = SavedStateHandle(mapOf("preset" to VideoQualityPreset.Quality.name))

        assertEquals(VideoQualityPreset.Quality, handle.readPreset("preset"))
    }

    @Test
    fun `readPreset falls back to Default for an absent key`() {
        val handle = SavedStateHandle()

        assertEquals(VideoQualityPreset.Default, handle.readPreset("preset"))
    }

    @Test
    fun `readPreset falls back to Default for an unrecognised string`() {
        val handle = SavedStateHandle(mapOf("preset" to "not-a-real-preset"))

        assertEquals(VideoQualityPreset.Default, handle.readPreset("preset"))
    }

    @Test
    fun `readCodec returns the right codec when the handle holds the enum constant`() {
        val handle = SavedStateHandle(mapOf("codec" to VideoCodecOption.Hevc))

        assertEquals(VideoCodecOption.Hevc, handle.readCodec("codec"))
    }

    @Test
    fun `readCodec returns the right codec when the handle holds the name string`() {
        val handle = SavedStateHandle(mapOf("codec" to VideoCodecOption.Hevc.name))

        assertEquals(VideoCodecOption.Hevc, handle.readCodec("codec"))
    }

    @Test
    fun `readCodec falls back to Default for an absent key`() {
        val handle = SavedStateHandle()

        assertEquals(VideoCodecOption.Default, handle.readCodec("codec"))
    }

    @Test
    fun `readCodec falls back to Default for an unrecognised string`() {
        val handle = SavedStateHandle(mapOf("codec" to "not-a-real-codec"))

        assertEquals(VideoCodecOption.Default, handle.readCodec("codec"))
    }
}
