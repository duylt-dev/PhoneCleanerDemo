package com.pion.phonecleaner.feature.photo.compressrun

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.photo.CompressStep
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.usecase.CompressPhotosUseCase
import com.pion.phonecleaner.feature.photo.testing.FakeCompressedPhotoLedger
import com.pion.phonecleaner.feature.photo.testing.FakePhotoCompressor
import com.pion.phonecleaner.feature.photo.testing.FakePhotoRepository
import com.pion.phonecleaner.feature.photo.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.photo.testing.RecordingAnalytics
import com.pion.phonecleaner.feature.photo.testing.photo
import com.pion.phonecleaner.feature.photo.testing.runVmTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** `docs/screens/13-photo-and-media.md` §4.2 and the seven deltas of §4.5. */
class CompressRunViewModelTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val repository = FakePhotoRepository(library = listOf(photo(1), photo(2), photo(3)))
    private val compressor = FakePhotoCompressor()
    private val analytics = RecordingAnalytics()
    private val ledger = FakeCompressedPhotoLedger()

    private fun viewModel(vararg ids: Long) = CompressRunViewModel(
        savedStateHandle = SavedStateHandle(mapOf("photoIds" to ids.toList())),
        compressPhotos = CompressPhotosUseCase(compressor, ledger),
        photos = repository,
        analytics = analytics,
    )

    @Test
    fun `an id that no longer resolves is dropped, and nothing left is sessionLost`() =
        main.runVmTest {
            val vm = viewModel(1L, 99L)
            vm.onIntent(CompressRunIntent.ScreenStarted)

            assertEquals(listOf(PhotoId(1)), vm.state.value.photos.map { it.id })
            assertFalse(vm.state.value.sessionLost)

            val lost = viewModel(99L)
            lost.onIntent(CompressRunIntent.ScreenStarted)
            assertTrue(lost.state.value.sessionLost)
        }

    @Test
    fun `the route's order is the user's order`() = main.runVmTest {
        val vm = viewModel(3L, 1L, 2L)

        vm.onIntent(CompressRunIntent.ScreenStarted)

        assertEquals(listOf(PhotoId(3), PhotoId(1), PhotoId(2)), vm.state.value.photos.map { it.id })
    }

    @Test
    fun `only the photos actually rewritten are recorded as compressed`() = main.runVmTest {
        compressor.steps = listOf(
            CompressStep(1, 3, PhotoId(1), beforeBytes = 1_000L, afterBytes = 400L, failed = false),
            // Failed: nothing was written, so nothing is recorded.
            CompressStep(2, 3, PhotoId(2), beforeBytes = 2_000L, afterBytes = 0L, failed = true),
            // Skipped: the re-encode was not smaller, so the file is untouched and still its own size.
            CompressStep(3, 3, PhotoId(3), beforeBytes = 3_000L, afterBytes = 3_000L, failed = false),
        )
        val vm = viewModel(1L, 2L, 3L)
        vm.onIntent(CompressRunIntent.ScreenStarted)
        vm.onIntent(CompressRunIntent.CompressAllPressed)
        vm.onIntent(CompressRunIntent.CompressConfirmed)

        // This is what keeps photo 1 on the picker's list once it has dropped under the size filter.
        assertEquals(setOf(PhotoId(1)), ledger.ids)
    }

    @Test
    fun `savedBytes is measured per photo and failures are counted, not swallowed`() =
        main.runVmTest {
            compressor.steps = listOf(
                CompressStep(1, 3, PhotoId(1), beforeBytes = 1_000L, afterBytes = 400L, failed = false),
                CompressStep(2, 3, PhotoId(2), beforeBytes = 2_000L, afterBytes = 0L, failed = true),
                CompressStep(3, 3, PhotoId(3), beforeBytes = 3_000L, afterBytes = 1_000L, failed = false),
            )
            val vm = viewModel(1L, 2L, 3L)
            vm.onIntent(CompressRunIntent.ScreenStarted)
            vm.onIntent(CompressRunIntent.CompressAllPressed)
            vm.onIntent(CompressRunIntent.CompressConfirmed)

            val run = requireNotNull(vm.state.value.run)
            // 600 + 2000, never `0.6 × selectedBytes` (§4.5).
            assertEquals(2_600L, run.savedBytes)
            assertEquals(2, run.done)
            assertEquals(1, run.failedCount)
            // A computed flag, not a latch that only ever goes up.
            assertTrue(run.isFinished)
            assertFalse(vm.state.value.isRunning)
        }

    @Test
    fun `the pager position never moves because a photo finished`() = main.runVmTest {
        compressor.steps = listOf(
            CompressStep(1, 2, PhotoId(1), 1_000L, 400L, failed = false),
            CompressStep(2, 2, PhotoId(2), 1_000L, 400L, failed = false),
        )
        val vm = viewModel(1L, 2L)
        vm.onIntent(CompressRunIntent.ScreenStarted)
        vm.onIntent(CompressRunIntent.PageChanged(1))

        vm.onIntent(CompressRunIntent.CompressAllPressed)
        vm.onIntent(CompressRunIntent.CompressConfirmed)

        assertEquals(1, vm.state.value.page)
        assertEquals(PhotoId(2), vm.state.value.run?.currentId)
    }

    @Test
    fun `the summary is raised by the completion animation, not by the last step`() =
        main.runVmTest {
            compressor.steps = listOf(CompressStep(1, 1, PhotoId(1), 1_000L, 250L, failed = false))
            val vm = viewModel(1L)
            vm.onIntent(CompressRunIntent.ScreenStarted)

            vm.effects.test {
                vm.onIntent(CompressRunIntent.CompressAllPressed)
                vm.onIntent(CompressRunIntent.CompressConfirmed)
                expectNoEvents()

                vm.onIntent(CompressRunIntent.CompletionAnimationFinished)
                val effect = awaitItem() as CompressRunEffect.NavigateToCleanResult
                assertEquals(FeatureId.PhotoCompressor, effect.summary.feature)
                assertEquals(750L, effect.summary.freedBytes)
                assertEquals(1, effect.summary.itemCount)
                assertEquals(CleanupOutcome.Cleaned, effect.summary.outcome)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `back while running asks before it cancels`() = main.runVmTest {
        compressor.hangs = true
        val vm = viewModel(1L)
        vm.onIntent(CompressRunIntent.ScreenStarted)
        vm.onIntent(CompressRunIntent.CompressAllPressed)
        assertTrue(vm.state.value.isCompressConfirmVisible)
        vm.onIntent(CompressRunIntent.CompressConfirmed)
        assertTrue(vm.state.value.isRunning)

        vm.onIntent(CompressRunIntent.BackPressed)

        // The competitor blocks back with a toast and never clears its `compressing` flag (§4.2).
        assertTrue(vm.state.value.isStopConfirmVisible)
        assertTrue(vm.state.value.isRunning)

        vm.effects.test {
            vm.onIntent(CompressRunIntent.CancelRunConfirmed)
            assertEquals(CompressRunEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertFalse(vm.state.value.isRunning)
        assertFalse(vm.state.value.isStopConfirmVisible)
    }

    @Test
    fun `an id that produced no step does not leave the run unfinished`() = main.runVmTest {
        // Two ids requested, one step emitted: the other photo had gone away.
        compressor.steps = listOf(CompressStep(1, 2, PhotoId(1), 1_000L, 400L, failed = false))
        val vm = viewModel(1L, 2L)
        vm.onIntent(CompressRunIntent.ScreenStarted)
        vm.onIntent(CompressRunIntent.CompressAllPressed)
        vm.onIntent(CompressRunIntent.CompressConfirmed)

        assertTrue(requireNotNull(vm.state.value.run).isFinished)
        assertFalse(vm.state.value.isRunning)
    }
}
