package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressOutcome
import com.pion.phonecleaner.domain.model.video.VideoCompressProgress
import com.pion.phonecleaner.domain.model.video.VideoCompressStep
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.testing.FakeCompressedVideoLedger
import com.pion.phonecleaner.domain.testing.FakeVideoCompressor
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `phase-03-domain-video-compression.md` step 9. */
class CompressVideosUseCaseTest {

    private val ledger = FakeCompressedVideoLedger()
    private val compressor = FakeVideoCompressor()
    private val useCase = CompressVideosUseCase(compressor, ledger)

    /** The OUTPUT id is recorded, not the source's — after the delete step the source row is gone. */
    @Test
    fun `a Compressed step records its output id, not its source id`() = runTest {
        compressor.emissions = listOf(
            VideoCompressProgress.Finished(
                VideoCompressStep(
                    index = 1,
                    total = 1,
                    id = "source-1",
                    beforeBytes = 1_000L,
                    afterBytes = 400L,
                    outcome = VideoCompressOutcome.Compressed,
                    outputId = "output-1",
                ),
            ),
        )

        useCase(listOf("source-1"), VideoQualityPreset.Balanced, VideoCodecOption.H264).toList()

        assertEquals(setOf("output-1"), ledger.recorded)
    }

    @Test
    fun `a NotSmaller step records nothing`() = runTest {
        compressor.emissions = listOf(
            VideoCompressProgress.Finished(
                VideoCompressStep(
                    index = 1,
                    total = 1,
                    id = "source-1",
                    beforeBytes = 1_000L,
                    afterBytes = 1_000L,
                    outcome = VideoCompressOutcome.NotSmaller,
                    outputId = null,
                ),
            ),
        )

        useCase(listOf("source-1"), VideoQualityPreset.Balanced, VideoCodecOption.H264).toList()

        assertTrue(ledger.recorded.isEmpty())
    }

    /** A percentage is not an outcome — recording on it would write the same id fifty times per video. */
    @Test
    fun `a Working arm records nothing`() = runTest {
        compressor.emissions = listOf(
            VideoCompressProgress.Working(index = 0, total = 1, id = "source-1", percent = 50),
        )

        useCase(listOf("source-1"), VideoQualityPreset.Balanced, VideoCodecOption.H264).toList()

        assertTrue(ledger.recorded.isEmpty())
    }
}
