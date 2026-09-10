package com.pion.phonecleaner.feature.files.videocompressrun

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressOutcome
import com.pion.phonecleaner.domain.model.video.VideoCompressProgress
import com.pion.phonecleaner.domain.model.video.VideoCompressStep
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.usecase.CheckSpaceForCompressionUseCase
import com.pion.phonecleaner.domain.usecase.CompressVideosUseCase
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.EstimateVideoCompressionUseCase
import com.pion.phonecleaner.feature.files.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.files.testing.FakeCleanupLedger
import com.pion.phonecleaner.feature.files.testing.FakeCompressedVideoLedger
import com.pion.phonecleaner.feature.files.testing.FakeFileDeleter
import com.pion.phonecleaner.feature.files.testing.FakePermissionRepository
import com.pion.phonecleaner.feature.files.testing.FakeStorageInfoRepository
import com.pion.phonecleaner.feature.files.testing.FakeTrashRepository
import com.pion.phonecleaner.feature.files.testing.FakeVideoCandidateRepository
import com.pion.phonecleaner.feature.files.testing.FakeVideoCompressor
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `videocompressrun` (`plans/260907-0142-video-compression/phase-07-run-screen.md`,
 * `phase-09-unit-tests.md` step 7).
 */
internal class VideoCompressRunViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val videoCandidates = FakeVideoCandidateRepository()
    private val compressor = FakeVideoCompressor()
    private val ledger = FakeCompressedVideoLedger()
    private val storage = FakeStorageInfoRepository()
    private val deleter = FakeFileDeleter()
    private val analytics = FakeAnalyticsRepository()

    private fun viewModel(
        ids: List<String> = listOf("v1"),
        preset: VideoQualityPreset = VideoQualityPreset.Balanced,
        codec: VideoCodecOption = VideoCodecOption.H264,
    ) = VideoCompressRunViewModel(
        savedState = SavedStateHandle(
            mapOf(
                VideoCompressRunViewModel.VIDEO_IDS_ARG to ids,
                VideoCompressRunViewModel.PRESET_ARG to preset,
                VideoCompressRunViewModel.CODEC_ARG to codec,
            ),
        ),
        compressVideos = CompressVideosUseCase(compressor, ledger),
        estimateCompression = EstimateVideoCompressionUseCase(),
        checkSpace = CheckSpaceForCompressionUseCase(storage),
        deleteFiles = DeleteFilesUseCase(deleter, FakeTrashRepository(), FakeCleanupLedger()),
        videos = videoCandidates,
        analytics = analytics,
        permissions = FakePermissionRepository(),
        log = AppLogger.NoOp,
    )

    // -- construction: the crash `VideoCompressRunReducersTest` also pins, exercised end to end -------

    /**
     * Real navigation hands an enum route argument back as the DECODED ENUM CONSTANT, and a
     * `List<String>` route argument may arrive as either a `List<*>` or an `Array<String>`. A test
     * that only ever put Strings in the handle would have passed while the app crashed on every
     * launch of this screen — which is exactly what happened on device (Samsung SM-A165F).
     */
    @Test
    fun `constructs without throwing from a handle shaped the way real navigation populates it`() {
        val listHandle = SavedStateHandle(
            mapOf(
                VideoCompressRunViewModel.VIDEO_IDS_ARG to listOf("v1", "v2"),
                VideoCompressRunViewModel.PRESET_ARG to VideoQualityPreset.Quality,
                VideoCompressRunViewModel.CODEC_ARG to VideoCodecOption.Hevc,
            ),
        )
        val fromList = VideoCompressRunViewModel(
            savedState = listHandle,
            compressVideos = CompressVideosUseCase(compressor, ledger),
            estimateCompression = EstimateVideoCompressionUseCase(),
            checkSpace = CheckSpaceForCompressionUseCase(storage),
            deleteFiles = DeleteFilesUseCase(deleter, FakeTrashRepository(), FakeCleanupLedger()),
            videos = videoCandidates,
            analytics = analytics,
            permissions = FakePermissionRepository(),
            log = AppLogger.NoOp,
        )
        assertEquals(VideoQualityPreset.Quality, fromList.state.value.preset)
        assertEquals(VideoCodecOption.Hevc, fromList.state.value.codec)

        val arrayHandle = SavedStateHandle(
            mapOf(
                VideoCompressRunViewModel.VIDEO_IDS_ARG to arrayOf("v1", "v2"),
                VideoCompressRunViewModel.PRESET_ARG to VideoQualityPreset.Saver,
                VideoCompressRunViewModel.CODEC_ARG to VideoCodecOption.H264,
            ),
        )
        val fromArray = VideoCompressRunViewModel(
            savedState = arrayHandle,
            compressVideos = CompressVideosUseCase(compressor, ledger),
            estimateCompression = EstimateVideoCompressionUseCase(),
            checkSpace = CheckSpaceForCompressionUseCase(storage),
            deleteFiles = DeleteFilesUseCase(deleter, FakeTrashRepository(), FakeCleanupLedger()),
            videos = videoCandidates,
            analytics = analytics,
            permissions = FakePermissionRepository(),
            log = AppLogger.NoOp,
        )
        assertEquals(VideoQualityPreset.Saver, fromArray.state.value.preset)
    }

    // -- reducers ------------------------------------------------------------------------------------

    @Test
    fun `ids that resolve to nothing set sessionLost`() = mainDispatcher.runVmTest {
        videoCandidates.pool = emptyList()
        val vm = viewModel(ids = listOf("missing"))

        vm.onIntent(VideoCompressRunIntent.ScreenStarted)
        settle()

        assertTrue(vm.state.value.sessionLost)
        assertTrue(vm.state.value.videos.isEmpty())
    }

    @Test
    fun `rows come back in the route's order, not the repository's`() = mainDispatcher.runVmTest {
        videoCandidates.pool = listOf(clip("b"), clip("a"))
        val vm = viewModel(ids = listOf("a", "b"))

        vm.onIntent(VideoCompressRunIntent.ScreenStarted)
        settle()

        assertEquals(listOf("a", "b"), vm.state.value.videos.map { it.id })
    }

    @Test
    fun `the run passes the route's codec to the compressor fake`() = mainDispatcher.runVmTest {
        videoCandidates.pool = listOf(clip("a"))
        val vm = viewModel(ids = listOf("a"), codec = VideoCodecOption.Hevc)
        vm.onIntent(VideoCompressRunIntent.ScreenStarted)
        settle()

        vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
        vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
        settle()

        assertEquals(VideoCodecOption.Hevc, compressor.lastCodec)
    }

    @Test
    fun `Working moves currentPercent and touches no counter`() = mainDispatcher.runVmTest {
        videoCandidates.pool = listOf(clip("a"))
        compressor.gate = CompletableDeferred()
        compressor.emissions = listOf(
            VideoCompressProgress.Working(index = 0, total = 1, id = "a", percent = 42),
        )
        val vm = viewModel(ids = listOf("a"))
        vm.onIntent(VideoCompressRunIntent.ScreenStarted)
        settle()

        vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
        vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
        settle()

        val run = requireNotNull(vm.state.value.run)
        assertEquals(42, run.currentPercent)
        assertEquals(0, run.done)
        assertEquals(0, run.skipped)
        assertEquals(0, run.failed)
        assertTrue(vm.state.value.isRunning)
    }

    @Test
    fun `three Finished arms produce done 1 skipped 1 failed 1 and isFinished`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a"), clip("b"), clip("c"))
            compressor.emissions = listOf(
                finished("a", outcome = VideoCompressOutcome.Compressed, outputId = "a-out", total = 3),
                finished("b", outcome = VideoCompressOutcome.NotSmaller, total = 3),
                finished("c", outcome = VideoCompressOutcome.Failed, total = 3),
            )
            val vm = viewModel(ids = listOf("a", "b", "c"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()

            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
            settle()

            val run = requireNotNull(vm.state.value.run)
            assertEquals(1, run.done)
            assertEquals(1, run.skipped)
            assertEquals(1, run.failed)
            assertTrue(run.isFinished)
        }

    /** Never `0.6 x selectedBytes` — the sum of what the engine actually measured, step by step. */
    @Test
    fun `savedBytes is the sum of the measured step savings, not a fraction of the selection`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a"), clip("b"))
            compressor.emissions = listOf(
                finished("a", beforeBytes = 1_000L, afterBytes = 400L, outputId = "a-out", total = 2),
                finished("b", beforeBytes = 2_000L, afterBytes = 500L, outputId = "b-out", total = 2),
            )
            val vm = viewModel(ids = listOf("a", "b"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()

            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
            settle()

            assertEquals(2_100L, requireNotNull(vm.state.value.run).savedBytes)
        }

    /** Left at the requested count, the bar would park forever — the natural end of a short flow. */
    @Test
    fun `when the flow ends early, total closes onto settled and isFinished becomes true`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a"), clip("b"))
            // Two ids requested and the engine planned two, but ONE step arrives: the other produced
            // nothing. Only a step reporting total = 2 leaves the closing line something to do.
            compressor.emissions = listOf(finished("a", outputId = "a-out", total = 2))
            val vm = viewModel(ids = listOf("a", "b"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()

            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
            settle()

            val run = requireNotNull(vm.state.value.run)
            assertEquals(1, run.total)
            assertTrue(run.isFinished)
        }

    @Test
    fun `currentPercent is null after every Finished`() = mainDispatcher.runVmTest {
        videoCandidates.pool = listOf(clip("a"))
        compressor.emissions = listOf(
            VideoCompressProgress.Working(index = 0, total = 1, id = "a", percent = 10),
            finished("a", outputId = "a-out"),
        )
        val vm = viewModel(ids = listOf("a"))
        vm.onIntent(VideoCompressRunIntent.ScreenStarted)
        settle()

        vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
        vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
        settle()

        assertNull(requireNotNull(vm.state.value.run).currentPercent)
    }

    // -- honesty ---------------------------------------------------------------------------------------

    /**
     * The feature's central honesty requirement: bytes were produced but nothing was reclaimed until
     * a delete actually runs, and no navigation happens off the compress step alone.
     */
    @Test
    fun `after a finished run with no delete, producedBytes is set and reclaimedBytes stays zero`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a"))
            compressor.emissions = listOf(finished("a", beforeBytes = 1_000L, afterBytes = 400L, outputId = "a-out"))
            val vm = viewModel(ids = listOf("a"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()
            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)

            vm.effects.test {
                vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
                settle()
                expectNoEvents()
            }

            assertTrue(vm.state.value.producedBytes > 0L)
            assertEquals(0L, vm.state.value.reclaimedBytes)
            assertTrue(vm.state.value.showOriginalsKeptNotice)
        }

    /**
     * The delete button's own figure. On the device a 22,9 MB source produced a 9,8 MB copy, and the
     * button read "giải phóng 13,6 MB" — [VideoCompressRunState.producedBytes], which is how much
     * *smaller* the copy is. Deleting that original frees the whole 22,9 MB. Understating a freed
     * figure is the same class of dishonesty as overstating one, and only the originals whose copy
     * actually exists are deletable at all — a skipped video's original is never touched.
     */
    @Test
    fun `deletableBytes is the size of the succeeded originals, not how much smaller the copies are`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a", sizeBytes = 24_000_000L), clip("b", sizeBytes = 8_000_000L))
            compressor.emissions = listOf(
                finished("a", beforeBytes = 24_000_000L, afterBytes = 9_800_000L, outputId = "a-out", total = 2),
                finished(
                    "b",
                    beforeBytes = 8_000_000L,
                    afterBytes = 8_000_000L,
                    outcome = VideoCompressOutcome.NotSmaller,
                    total = 2,
                ),
            )
            val vm = viewModel(ids = listOf("a", "b"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()
            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
            settle()

            // Only "a" was re-encoded, so only "a"'s original is on the chopping block.
            assertEquals(24_000_000L, vm.state.value.deletableBytes)
            assertEquals(14_200_000L, vm.state.value.producedBytes)
        }

    // -- space -------------------------------------------------------------------------------------

    @Test
    fun `a tiny available space sets spaceShortfall and never starts the compressor`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a"))
            storage.available = 1L * 1024L * 1024L
            val vm = viewModel(ids = listOf("a"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()

            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            settle()

            assertTrue(vm.state.value.spaceShortfall != null)
            assertEquals(0, compressor.calls)
        }

    @Test
    fun `a row with an unusable estimate does not by itself cause a refusal`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a", durationMs = 0L))
            val vm = viewModel(ids = listOf("a"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()

            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            settle()

            assertNull(vm.state.value.spaceShortfall)
            assertTrue(vm.state.value.isRunConfirmVisible)
        }

    // -- consent -----------------------------------------------------------------------------------

    @Test
    fun `PendingConsent round trip re-issues the delete with the same ids and reaches NavigateToCleanResult`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a", sizeBytes = 10_000L))
            compressor.emissions = listOf(finished("a", beforeBytes = 10_000L, afterBytes = 4_000L, outputId = "a-out"))
            deleter.outcomes = mutableListOf(
                AppResult.Success(
                    DeleteOutcome.PendingConsent(
                        request = PendingIntentToken("token"),
                        ids = persistentListOf("a"),
                    ),
                ),
            )
            val vm = viewModel(ids = listOf("a"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()
            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
            settle()

            vm.effects.test {
                vm.onIntent(VideoCompressRunIntent.DeleteOriginalsPressed)
                vm.onIntent(VideoCompressRunIntent.DeleteOriginalsConfirmed)
                settle()
                val consent = awaitItem() as VideoCompressRunEffect.RequestDeleteConsent
                assertEquals(PendingIntentToken("token"), consent.token)

                vm.onIntent(VideoCompressRunIntent.DeleteConsentResult(granted = true))
                settle()
                val navigate = awaitItem() as VideoCompressRunEffect.NavigateToCleanResult
                assertEquals(10_000L, navigate.summary.freedBytes)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(listOf("a"), listOf("a")), deleter.requested.map { files -> files.map { it.id } })
            assertEquals(10_000L, vm.state.value.reclaimedBytes)
        }

    @Test
    fun `DeleteConsentResult false emits nothing and leaves reclaimedBytes zero`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a"))
            compressor.emissions = listOf(finished("a", beforeBytes = 1_000L, afterBytes = 400L, outputId = "a-out"))
            deleter.outcomes = mutableListOf(
                AppResult.Success(
                    DeleteOutcome.PendingConsent(
                        request = PendingIntentToken("token"),
                        ids = persistentListOf("a"),
                    ),
                ),
            )
            val vm = viewModel(ids = listOf("a"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()
            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
            settle()
            // Inside the turbine: effects are Channel(BUFFERED), so a consent request raised before
            // subscribing is delivered on subscribe and would read as "the decline emitted something".
            vm.effects.test {
                vm.onIntent(VideoCompressRunIntent.DeleteOriginalsPressed)
                vm.onIntent(VideoCompressRunIntent.DeleteOriginalsConfirmed)
                settle()
                assertTrue(awaitItem() is VideoCompressRunEffect.RequestDeleteConsent)

                vm.onIntent(VideoCompressRunIntent.DeleteConsentResult(granted = false))
                settle()
                expectNoEvents()
            }
            assertEquals(0L, vm.state.value.reclaimedBytes)
        }

    // -- cancel --------------------------------------------------------------------------------------

    /**
     * Pins the second ambiguity phase 06/07 flagged: a Stop mid-run must close `total` onto what
     * actually settled so `isFinished` becomes true, keep `run` (never null it), and leave the delete
     * step reachable for the videos that DID finish before the stop.
     */
    @Test
    fun `StopConfirmed mid-run cancels, keeps run, and leaves the finished outputs deletable`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a"), clip("b"), clip("c"))
            compressor.gate = CompletableDeferred()
            compressor.emissions = listOf(
                finished("a", beforeBytes = 1_000L, afterBytes = 400L, outputId = "a-out", total = 3),
                finished("b", beforeBytes = 1_000L, afterBytes = 400L, outputId = "b-out", total = 3),
            )
            val vm = viewModel(ids = listOf("a", "b", "c"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()
            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
            settle()
            // Two of three landed; the third is still "running" behind the gate.
            assertTrue(vm.state.value.isRunning)

            vm.onIntent(VideoCompressRunIntent.StopConfirmed)
            settle()

            val run = requireNotNull(vm.state.value.run)
            assertEquals(2, run.total)
            assertTrue(run.isFinished)
            assertTrue(vm.state.value.canDeleteOriginals)

            // The delete step is still reachable — this is the whole point of closing `total`.
            vm.onIntent(VideoCompressRunIntent.DeleteOriginalsPressed)
            assertTrue(vm.state.value.isDeleteConfirmVisible)
            vm.onIntent(VideoCompressRunIntent.DeleteOriginalsConfirmed)
            settle()
            assertEquals(setOf("a", "b"), deleter.requested.last().map { it.id }.toSet())
        }

    // -- crash containment ---------------------------------------------------------------------------

    @Test
    fun `a throwing compressor leaves an Unexpected error and clears every dialog flag`() =
        mainDispatcher.runVmTest {
            videoCandidates.pool = listOf(clip("a"))
            compressor.throwOnCollect = true
            val vm = viewModel(ids = listOf("a"))
            vm.onIntent(VideoCompressRunIntent.ScreenStarted)
            settle()

            vm.onIntent(VideoCompressRunIntent.CompressAllPressed)
            vm.onIntent(VideoCompressRunIntent.CompressConfirmed)
            settle()

            assertTrue(vm.state.value.error is AppError.Unexpected)
            assertFalse(vm.state.value.isRunConfirmVisible)
            assertFalse(vm.state.value.isStopConfirmVisible)
            assertFalse(vm.state.value.isDeleteConfirmVisible)
        }

    private companion object {
        fun clip(
            id: String,
            sizeBytes: Long = 30L * 1024L * 1024L,
            durationMs: Long = 60_000L,
            width: Int = 1920,
            height: Int = 1080,
        ) = VideoCandidate(
            file = ScannedFile(
                id = id,
                path = "/movies/$id.mp4",
                name = "$id.mp4",
                sizeBytes = sizeBytes,
                kind = FileKind.Video,
                origin = FileOrigin.MediaStoreEntry("content://video/$id"),
            ),
            durationMs = durationMs,
            width = width,
            height = height,
        )

        /**
         * [total] is the count the **engine** reports, which [VideoRunProgress.fold] trusts over the
         * requested count (`total = step.total`, the photo side's rule: an id that no longer resolves
         * never produces a step). Leaving it at 1 for a multi-video run emits a figure the real
         * compressor never emits, and makes a run read as finished on its first step.
         */
        fun finished(
            id: String,
            beforeBytes: Long = 1_000L,
            afterBytes: Long = 400L,
            outcome: VideoCompressOutcome = VideoCompressOutcome.Compressed,
            outputId: String? = null,
            total: Int = 1,
        ) = VideoCompressProgress.Finished(
            VideoCompressStep(
                index = 0,
                total = total,
                id = id,
                beforeBytes = beforeBytes,
                afterBytes = afterBytes,
                outcome = outcome,
                outputId = outputId,
            ),
        )
    }
}
