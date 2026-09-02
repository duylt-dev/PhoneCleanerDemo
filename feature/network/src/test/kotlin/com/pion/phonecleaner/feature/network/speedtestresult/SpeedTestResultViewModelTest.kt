package com.pion.phonecleaner.feature.network.speedtestresult

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.feature.network.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.network.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.network.testing.runVmTest
import com.pion.phonecleaner.feature.network.testing.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class SpeedTestResultViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val featureUsage = FakeFeatureUsageRepository()

    private fun viewModel(download: Long? = null, upload: Long? = null): SpeedTestResultViewModel {
        val handle = SavedStateHandle()
        download?.let { handle[SpeedTestResultViewModel.ARG_DOWNLOAD_BPS] = it }
        upload?.let { handle[SpeedTestResultViewModel.ARG_UPLOAD_BPS] = it }
        return SpeedTestResultViewModel(
            savedState = handle,
            markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
            log = AppLogger.NoOp,
        )
    }

    /** The arguments ARE the initial state — not copied in a frame later (MVI §3). */
    @Test
    fun `the route arguments are the initial state`() = mainDispatcher.runVmTest {
        val vm = viewModel(download = 9_000L, upload = 3_000L)

        assertEquals(9_000L, vm.state.value.downloadBytesPerSecond)
        assertEquals(3_000L, vm.state.value.uploadBytesPerSecond)
        assertTrue(vm.state.value.isMeasured)
    }

    /**
     * A route entered with no measurement carries the `0L` defaults. That is NOT a measurement of
     * zero, and the screen must be able to tell the difference — otherwise it renders `0 B/s` as
     * though it had been measured, which is the fabrication this cluster exists to avoid.
     */
    @Test
    fun `absent arguments are not a measurement of zero`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        assertFalse(vm.state.value.isMeasured)
    }

    /** The one decision this screen has. */
    @Test
    fun `opening the screen records the feature use`() = mainDispatcher.runVmTest {
        viewModel(download = 1L)
        settle()

        assertEquals(listOf(FeatureId.NetworkTest), featureUsage.marked)
    }

    @Test
    fun `done and back both navigate back`() = mainDispatcher.runVmTest {
        val vm = viewModel(download = 1L)

        vm.effects.test {
            vm.onIntent(SpeedTestResultIntent.DonePressed)
            assertTrue(awaitItem() is SpeedTestResultEffect.NavigateBack)

            vm.onIntent(SpeedTestResultIntent.BackPressed)
            assertTrue(awaitItem() is SpeedTestResultEffect.NavigateBack)
        }
    }

    /** New here: the competitor's result screen cannot repeat the test (§2.4 D-3). */
    @Test
    fun `run again asks to return to the test`() = mainDispatcher.runVmTest {
        val vm = viewModel(download = 1L)

        vm.effects.test {
            vm.onIntent(SpeedTestResultIntent.RunAgainPressed)
            assertTrue(awaitItem() is SpeedTestResultEffect.NavigateToTest)
        }
    }
}
