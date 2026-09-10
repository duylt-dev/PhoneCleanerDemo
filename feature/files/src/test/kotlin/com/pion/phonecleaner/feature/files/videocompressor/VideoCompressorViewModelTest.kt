package com.pion.phonecleaner.feature.files.videocompressor

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.usecase.EstimateVideoCompressionUseCase
import com.pion.phonecleaner.domain.usecase.LoadCompressibleVideosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.feature.files.component.MediaAccess
import com.pion.phonecleaner.feature.files.testing.FakeAnalyticsRepository
import com.pion.phonecleaner.feature.files.testing.FakeCompressedVideoLedger
import com.pion.phonecleaner.feature.files.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.files.testing.FakeVideoCandidateRepository
import com.pion.phonecleaner.feature.files.testing.FakeVideoEncoderCapabilities
import com.pion.phonecleaner.feature.files.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.files.testing.runVmTest
import com.pion.phonecleaner.feature.files.testing.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `videocompressor` — the picker (`plans/260907-0142-video-compression/phase-06-picker-screen.md`,
 * `phase-09-unit-tests.md` step 6).
 */
internal class VideoCompressorViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val repository = FakeVideoCandidateRepository()
    private val ledger = FakeCompressedVideoLedger()
    private val capabilities = FakeVideoEncoderCapabilities()
    private val analytics = FakeAnalyticsRepository()

    private fun viewModel(savedState: SavedStateHandle = SavedStateHandle()) = VideoCompressorViewModel(
        savedState = savedState,
        loadCompressibleVideos = LoadCompressibleVideosUseCase(repository, ledger),
        estimateCompression = EstimateVideoCompressionUseCase(),
        capabilities = capabilities,
        markFeatureUsed = MarkFeatureUsedUseCase(FakeFeatureUsageRepository()),
        analytics = analytics,
        log = AppLogger.NoOp,
    )

    @Test
    fun `PermissionResolved Granted loads the candidates`() = mainDispatcher.runVmTest {
        repository.pool = listOf(clip("v1"))
        val vm = viewModel()

        vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
        settle()

        assertEquals(1, vm.state.value.videos.items.size)
    }

    /** `Denied` renders the permission state and never reaches the repository. */
    @Test
    fun `PermissionResolved Denied renders the permission state and loads nothing`() =
        mainDispatcher.runVmTest {
            repository.pool = listOf(clip("v1"))
            val vm = viewModel()

            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Denied))
            settle()

            assertTrue(vm.state.value.showPermissionState)
            assertTrue(vm.state.value.videos.items.isEmpty())
            assertEquals(0, repository.calls)
        }

    @Test
    fun `selecting two rows sets selectedCount and canContinue`() = mainDispatcher.runVmTest {
        repository.pool = listOf(clip("v1"), clip("v2"))
        val vm = viewModel()
        vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
        settle()
        vm.onIntent(VideoCompressorIntent.CompletionAnimationFinished)

        vm.onIntent(VideoCompressorIntent.RowToggled("v1"))
        vm.onIntent(VideoCompressorIntent.RowToggled("v2"))

        assertEquals(2, vm.state.value.selectedCount)
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `changing the preset recomputes the estimate`() = mainDispatcher.runVmTest {
        repository.pool = listOf(clip("v1"))
        val vm = viewModel()
        vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
        settle()
        vm.onIntent(VideoCompressorIntent.CompletionAnimationFinished)
        vm.onIntent(VideoCompressorIntent.RowToggled("v1"))
        val before = requireNotNull(vm.state.value.estimate).estimatedAfterBytes

        vm.onIntent(VideoCompressorIntent.PresetSelected(VideoQualityPreset.Saver))

        val after = requireNotNull(vm.state.value.estimate).estimatedAfterBytes
        assertTrue(after != before)
    }

    /** `null`, never a zeroed [com.pion.phonecleaner.domain.model.video.VideoCompressionEstimate]. */
    @Test
    fun `an empty selection sets estimate to null, not a zeroed object`() = mainDispatcher.runVmTest {
        repository.pool = listOf(clip("v1"))
        val vm = viewModel()
        vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
        settle()
        vm.onIntent(VideoCompressorIntent.CompletionAnimationFinished)
        vm.onIntent(VideoCompressorIntent.RowToggled("v1"))
        assertTrue(vm.state.value.estimate != null)

        vm.onIntent(VideoCompressorIntent.RowToggled("v1"))

        assertNull(vm.state.value.estimate)
    }

    /**
     * D7: select-all skips an `alreadyCompressed` row, but a deliberate tap on that same row still
     * selects it — only select-all passes it over.
     */
    @Test
    fun `select-all skips an alreadyCompressed row, but a direct toggle still selects it`() =
        mainDispatcher.runVmTest {
            repository.pool = listOf(clip("compressed-1"), clip("v2"))
            ledger.recorded += "compressed-1"
            val vm = viewModel()
            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
            settle()
            vm.onIntent(VideoCompressorIntent.CompletionAnimationFinished)

            vm.onIntent(VideoCompressorIntent.SelectAllToggled)
            assertEquals(setOf("v2"), vm.state.value.videos.selectedIds)

            vm.onIntent(VideoCompressorIntent.RowToggled("compressed-1"))
            assertEquals(setOf("v2", "compressed-1"), vm.state.value.videos.selectedIds)
        }

    /** The number must fall H.264 -> HEVC, never rise and never stay put. */
    @Test
    fun `changing the codec recomputes the estimate and it falls from H264 to HEVC`() =
        mainDispatcher.runVmTest {
            repository.pool = listOf(clip("v1"))
            val vm = viewModel()
            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
            settle()
            vm.onIntent(VideoCompressorIntent.CompletionAnimationFinished)
            vm.onIntent(VideoCompressorIntent.RowToggled("v1"))
            val h264Estimate = requireNotNull(vm.state.value.estimate).estimatedAfterBytes

            vm.onIntent(VideoCompressorIntent.CodecSelected(VideoCodecOption.Hevc))

            val hevcEstimate = requireNotNull(vm.state.value.estimate).estimatedAfterBytes
            assertTrue(hevcEstimate < h264Estimate)
        }

    /**
     * The ONLY coverage this state has: the real test device carries a hardware HEVC encoder and can
     * never render the disabled state on its own (`phase-09-unit-tests.md` key insight 5). Also
     * verifies `isSupported(H264)` stays `true` even when HEVC is unsupported — H.264 is never the
     * codec that gets refused.
     */
    @Test
    fun `no hardware HEVC encoder renders isHevcAvailable false, H264 still true`() =
        mainDispatcher.runVmTest {
            capabilities.hevcSupported = false
            val vm = viewModel()
            settle()

            assertFalse(vm.state.value.isHevcAvailable)
            assertTrue(capabilities.isSupported(VideoCodecOption.H264))
        }

    /** A restored preference from other hardware must not start an impossible run. */
    @Test
    fun `with HEVC disabled, CodecSelected Hevc is ignored`() = mainDispatcher.runVmTest {
        capabilities.hevcSupported = false
        val vm = viewModel()
        settle()

        vm.onIntent(VideoCompressorIntent.CodecSelected(VideoCodecOption.Hevc))

        assertEquals(VideoCodecOption.H264, vm.state.value.codec)
    }

    @Test
    fun `a hardware HEVC encoder renders isHevcAvailable true`() = mainDispatcher.runVmTest {
        capabilities.hevcSupported = true
        val vm = viewModel()
        settle()

        assertTrue(vm.state.value.isHevcAvailable)
    }

    @Test
    fun `ContinuePressed emits OpenVideoCompressRun carrying ids, preset and codec as scalars`() =
        mainDispatcher.runVmTest {
            repository.pool = listOf(clip("v1"))
            val vm = viewModel()
            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
            settle()
            vm.onIntent(VideoCompressorIntent.CompletionAnimationFinished)
            vm.onIntent(VideoCompressorIntent.RowToggled("v1"))
            vm.onIntent(VideoCompressorIntent.PresetSelected(VideoQualityPreset.Quality))

            vm.effects.test {
                vm.onIntent(VideoCompressorIntent.ContinuePressed)
                val effect = awaitItem() as VideoCompressorEffect.OpenVideoCompressRun
                assertEquals(listOf("v1"), effect.ids)
                assertEquals(VideoQualityPreset.Quality, effect.preset)
                assertEquals(VideoCodecOption.H264, effect.codec)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `ContinuePressed with an empty selection emits nothing`() = mainDispatcher.runVmTest {
        repository.pool = listOf(clip("v1"))
        val vm = viewModel()
        vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
        settle()
        vm.onIntent(VideoCompressorIntent.CompletionAnimationFinished)

        vm.effects.test {
            vm.onIntent(VideoCompressorIntent.ContinuePressed)
            expectNoEvents()
        }
    }

    @Test
    fun `a throwing repository leaves an Unexpected error and phase Ready`() = mainDispatcher.runVmTest {
        repository.throwOnCandidates = true
        val vm = viewModel()

        vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
        settle()

        assertTrue(vm.state.value.error is AppError.Unexpected)
        assertEquals(ToolPhase.Ready, vm.state.value.phase)
    }

    /**
     * `settle()` past the 30s query timeout. `VideoCompressorState` carries no `scanTruncated` field
     * (unlike `VideoManagerState`/`BigFilesState`/`AudioManagerState`/`DuplicatesState`) because
     * `LoadCompressibleVideosUseCase` is one suspend `AppResult` call, not a Flow of partial progress
     * — a timeout here means NO rows were obtained, not a partial scan, so `VideoCompressorViewModel`
     * folds it into the general failure path (`onFailure`) instead of a truncation flag. The spinner
     * still has to come down either way — that is what this test pins.
     */
    @Test
    fun `a repository that never answers still brings the spinner down at the timeout`() =
        mainDispatcher.runVmTest {
            repository.hangs = true
            val vm = viewModel()

            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
            settle()

            assertEquals(ToolPhase.Ready, vm.state.value.phase)
            assertTrue(vm.state.value.error is AppError.Unexpected)
        }

    /**
     * `LLM.md` §11 row 11 — the `SimilarPhotosViewModel` inert-retry defect must not be copied in:
     * after a failure, a second `PermissionResolved(Granted)` must actually reach the repository
     * again, not silently no-op.
     */
    @Test
    fun `after a failure, a second PermissionResolved Granted actually re-loads`() =
        mainDispatcher.runVmTest {
            repository.throwOnCandidates = true
            val vm = viewModel()
            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
            settle()
            assertEquals(1, repository.calls)
            assertTrue(vm.state.value.error != null)

            repository.throwOnCandidates = false
            repository.pool = listOf(clip("v1"))
            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
            settle()

            assertEquals(2, repository.calls)
            assertEquals(1, vm.state.value.videos.items.size)
            assertNull(vm.state.value.error)
        }

    /**
     * The button, not the lifecycle. `ErrorCard`'s retry raises `ScreenStarted` and nothing else — no
     * `PermissionResolved` follows it — so the test above passes while the button itself does nothing.
     * That is `LLM.md` §11 row 11, and the only test that can see it drives `ScreenStarted` alone.
     */
    @Test
    fun `the retry button alone re-scans after a failed load`() =
        mainDispatcher.runVmTest {
            repository.throwOnCandidates = true
            val vm = viewModel()
            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Granted))
            settle()
            assertEquals(1, repository.calls)
            assertTrue(vm.state.value.error != null)

            repository.throwOnCandidates = false
            repository.pool = listOf(clip("v1"))
            vm.onIntent(VideoCompressorIntent.ScreenStarted)
            settle()

            assertEquals(2, repository.calls)
            assertEquals(1, vm.state.value.videos.items.size)
            assertNull(vm.state.value.error)
        }

    /** A retry that never got permission must not reach the repository at all. */
    @Test
    fun `the retry button does nothing while access is denied`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()
            vm.onIntent(VideoCompressorIntent.PermissionResolved(MediaAccess.Denied))
            settle()
            val callsAfterDenial = repository.calls

            vm.onIntent(VideoCompressorIntent.ScreenStarted)
            settle()

            assertEquals(callsAfterDenial, repository.calls)
        }

    /**
     * Pins the ambiguity phase 06/07 flagged: `StartPressed` only dismisses the intro panel. Its own
     * `load()` call is a defensive fallback for a state normal flow never reaches — by the time a user
     * can tap it, `ON_START`'s `PermissionResolved` has already moved `phase` off `Idle` (or, as here,
     * `access` is still `Unknown` and `canLoad` is `false`) — so the repository is never touched.
     */
    @Test
    fun `StartPressed alone dismisses the intro but does not load`() = mainDispatcher.runVmTest {
        repository.pool = listOf(clip("v1"))
        val vm = viewModel()

        vm.onIntent(VideoCompressorIntent.StartPressed)
        settle()

        assertFalse(vm.state.value.introVisible)
        assertEquals(0, repository.calls)
        assertEquals(ToolPhase.Idle, vm.state.value.phase)
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
    }
}
