package com.pion.phonecleaner.feature.photo.compressor

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.photo.CompressionEstimate
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.usecase.EstimateCompressionUseCase
import com.pion.phonecleaner.domain.usecase.LoadCompressiblePhotosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.feature.photo.testing.FakePhotoCompressor
import com.pion.phonecleaner.feature.photo.testing.FakePhotoRepository
import com.pion.phonecleaner.feature.photo.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.photo.testing.RecordingAnalytics
import com.pion.phonecleaner.feature.photo.testing.RecordingFeatureUsage
import com.pion.phonecleaner.feature.photo.testing.photo
import com.pion.phonecleaner.feature.photo.testing.runVmTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** `docs/screens/13-photo-and-media.md` §3.2 and the six deltas of §3.5. */
class PhotoCompressorViewModelTest {

    @get:Rule internal val main = MainDispatcherRule()

    private val repository = FakePhotoRepository(
        // One photo is under LoadCompressiblePhotosUseCase.MIN_COMPRESSIBLE_BYTES and must not be
        // offered: re-encoding a thumbnail makes it larger (§3.5).
        library = listOf(
            photo(1, sizeBytes = 4_000_000L),
            photo(2, sizeBytes = 3_000_000L),
            photo(3, sizeBytes = 1_000L),
        ),
    )
    private val compressor = FakePhotoCompressor()
    private val analytics = RecordingAnalytics()
    private val usage = RecordingFeatureUsage()

    private fun viewModel() = PhotoCompressorViewModel(
        savedStateHandle = SavedStateHandle(),
        loadCompressiblePhotos = LoadCompressiblePhotosUseCase(repository),
        estimateSavings = EstimateCompressionUseCase(compressor),
        markFeatureUsed = MarkFeatureUsedUseCase(usage),
        analytics = analytics,
    )

    @Test
    fun `opening the screen does not scan - Start does`() = main.runVmTest {
        val vm = viewModel()

        vm.onIntent(PhotoCompressorIntent.ScreenStarted)

        assertEquals(ToolPhase.Idle, vm.state.value.phase)
        assertTrue(vm.state.value.introVisible)
        assertTrue(vm.state.value.groups.isEmpty())
        assertEquals(listOf(AnalyticsEvent.FeatureOpened(FeatureId.PhotoCompressor)), analytics.events)

        vm.onIntent(PhotoCompressorIntent.StartPressed)

        assertEquals(ToolPhase.Completing, vm.state.value.phase)
        assertEquals(listOf(FeatureId.PhotoCompressor), usage.used)
    }

    @Test
    fun `photos below the minimum size are never offered`() = main.runVmTest {
        val vm = viewModel()

        vm.onIntent(PhotoCompressorIntent.StartPressed)

        val offered = vm.state.value.groups.flatMap { it.photos }.map { it.id }
        assertEquals(setOf(PhotoId(1), PhotoId(2)), offered.toSet())
    }

    @Test
    fun `an empty selection cannot reach the run screen`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(PhotoCompressorIntent.StartPressed)
        vm.onIntent(PhotoCompressorIntent.CompletionAnimationFinished)

        // The competitor's Continue with nothing selected is a silent no-op; here the reducer
        // refuses and the button is disabled by the same `canContinue` (§3.5).
        assertEquals(0, vm.state.value.selectedCount)
        assertTrue(!vm.state.value.canContinue)
        vm.onIntent(PhotoCompressorIntent.ContinuePressed)

        vm.onIntent(PhotoCompressorIntent.PhotoToggled(PhotoId(1)))
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `select all is set arithmetic and toggles back off`() = main.runVmTest {
        val vm = viewModel()
        vm.onIntent(PhotoCompressorIntent.StartPressed)

        vm.onIntent(PhotoCompressorIntent.SelectAllToggled)
        assertEquals(2, vm.state.value.selectedCount)
        assertEquals(7_000_000L, vm.state.value.selectedBytes)

        vm.onIntent(PhotoCompressorIntent.SelectAllToggled)
        assertEquals(0, vm.state.value.selectedCount)
    }

    @Test
    fun `the estimate is measured through the encoder, never a constant`() = main.runVmTest {
        compressor.estimate = AppResult.Success(
            CompressionEstimate(beforeBytes = 900_000L, afterBytes = 400_000L, sampledCount = 3),
        )
        val vm = viewModel()

        vm.onIntent(PhotoCompressorIntent.StartPressed)

        assertEquals(500_000L, vm.state.value.estimate?.savedBytes)
        assertEquals(3, vm.state.value.estimate?.sampledCount)
        // Measured over the candidates, not over the selection: no bitmap is decoded inside a tap.
        assertEquals(setOf(PhotoId(1), PhotoId(2)), compressor.estimatedIds.toSet())
    }

    @Test
    fun `a failed estimate leaves the list working`() = main.runVmTest {
        compressor.estimate = AppResult.Failure(com.pion.phonecleaner.core.common.error.AppError.Unexpected(null))
        val vm = viewModel()

        vm.onIntent(PhotoCompressorIntent.StartPressed)

        assertNull(vm.state.value.estimate)
        assertNull(vm.state.value.error)
        assertEquals(1, vm.state.value.groups.size)
    }
}
