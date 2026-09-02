package com.pion.phonecleaner.feature.network.speedtest

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.network.SpeedResult
import com.pion.phonecleaner.domain.model.network.SpeedSample
import com.pion.phonecleaner.domain.model.network.SpeedTestProgress
import com.pion.phonecleaner.domain.model.network.SpeedTestStage
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.RunSpeedTestUseCase
import com.pion.phonecleaner.feature.network.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.network.testing.FakeSpeedTestRepository
import com.pion.phonecleaner.feature.network.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.network.testing.runVmTest
import com.pion.phonecleaner.feature.network.testing.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

internal class SpeedTestViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val speedTest = FakeSpeedTestRepository()
    private val featureUsage = FakeFeatureUsageRepository()

    private fun viewModel() = SpeedTestViewModel(
        runSpeedTest = RunSpeedTestUseCase(speedTest),
        markFeatureUsed = MarkFeatureUsedUseCase(featureUsage),
        log = AppLogger.NoOp,
    )

    /**
     * The shipped behaviour, and the reason this screen exists in this shape: PENDING OWNER DECISION
     * 2 leaves no byte source, the repository measures nothing, and the screen must say so. **No
     * figure of any kind may be on state afterwards** — not a rate, not a zero.
     */
    @Test
    fun `an unconfigured byte source reports not measured and no figure`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()

            vm.onIntent(SpeedTestIntent.ScreenStarted)
            settle()

            assertEquals(SpeedTestState.Phase.NotConfigured, vm.state.value.phase)
            assertNull(vm.state.value.currentBytesPerSecond)
            assertEquals(0, vm.state.value.progressPercent)
            assertNull(vm.state.value.error)
        }

    /** Not configured is not a failure of this run, so nothing offers to retry it. */
    @Test
    fun `not configured offers no retry`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.onIntent(SpeedTestIntent.ScreenStarted)
        settle()

        assertFalse(vm.state.value.canRetry)
    }

    /** Nothing is recorded as a feature use for a run that measured nothing. */
    @Test
    fun `not configured records no feature use and raises no navigation`() =
        mainDispatcher.runVmTest {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(SpeedTestIntent.ScreenStarted)
                settle()
                expectNoEvents()
            }
            assertTrue(featureUsage.marked.isEmpty())
        }

    /** ON_START arrives on every return; only the first one starts a run. */
    @Test
    fun `a second screen started does not restart the run`() = mainDispatcher.runVmTest {
        val vm = viewModel()

        vm.onIntent(SpeedTestIntent.ScreenStarted)
        settle()
        vm.onIntent(SpeedTestIntent.ScreenStarted)
        settle()

        assertEquals(1, speedTest.calls)
    }

    /** A measured run carries both numbers on the Effect — the collector never reads them off state. */
    @Test
    fun `a finished measurement navigates with its two numbers`() = mainDispatcher.runVmTest {
        speedTest.emissions = listOf(
            SpeedTestProgress.Running(SpeedSample(SpeedTestStage.Download, 50, 4_000L)),
            SpeedTestProgress.Finished(SpeedResult(8_000L, 2_000L)),
        )
        val vm = viewModel()

        vm.effects.test {
            vm.onIntent(SpeedTestIntent.ScreenStarted)
            settle()

            val effect = awaitItem()
            assertTrue(effect is SpeedTestEffect.NavigateToResult)
            effect as SpeedTestEffect.NavigateToResult
            assertEquals(8_000L, effect.downloadBps)
            assertEquals(2_000L, effect.uploadBps)
        }
        assertEquals(listOf(FeatureId.NetworkTest), featureUsage.marked)
        assertEquals(SpeedTestState.Phase.Finished, vm.state.value.phase)
    }

    /** A live sample reaches state; the number on screen is the number in state (D-1). */
    @Test
    fun `a sample updates the position and the live rate`() = mainDispatcher.runVmTest {
        speedTest.emissions = listOf(
            SpeedTestProgress.Running(SpeedSample(SpeedTestStage.Upload, 42, 1_500L)),
        )
        // The run stays open: a flow that emits samples and then completes without a result is a
        // FAILED run, and the failure reducer clears the figure on purpose.
        speedTest.neverCompletes = true
        val vm = viewModel()

        vm.onIntent(SpeedTestIntent.ScreenStarted)
        settle(WhileRunningMillis)

        assertEquals(42, vm.state.value.progressPercent)
        assertEquals(1_500L, vm.state.value.currentBytesPerSecond)
        assertEquals(SpeedTestStage.Upload, vm.state.value.stage)
    }

    /** Back while running asks first, and the prompt is state so a rotation keeps it. */
    @Test
    fun `back while running raises the abandon prompt instead of navigating`() =
        mainDispatcher.runVmTest {
            speedTest.emissions = emptyList()
            speedTest.neverCompletes = true
            val vm = viewModel()
            vm.onIntent(SpeedTestIntent.ScreenStarted)
            settle(WhileRunningMillis)

            vm.effects.test {
                vm.onIntent(SpeedTestIntent.BackPressed)
                expectNoEvents()
            }
            assertTrue(vm.state.value.isAbandonPromptVisible)
        }

    /** Confirming the abandon cancels the run and leaves; the phase goes back to Idle, not Running. */
    @Test
    fun `abandoning cancels the run and navigates back`() = mainDispatcher.runVmTest {
        speedTest.emissions = emptyList()
        speedTest.neverCompletes = true
        val vm = viewModel()
        vm.onIntent(SpeedTestIntent.ScreenStarted)
        settle(WhileRunningMillis)
        vm.onIntent(SpeedTestIntent.BackPressed)

        vm.effects.test {
            vm.onIntent(SpeedTestIntent.AbandonConfirmed)

            assertTrue(awaitItem() is SpeedTestEffect.NavigateBack)
        }
        assertEquals(SpeedTestState.Phase.Idle, vm.state.value.phase)
        assertFalse(vm.state.value.isAbandonPromptVisible)
    }

    /** A repository that throws lands on a terminal phase with an error, never on a stuck spinner. */
    @Test
    fun `a throwing measurement leaves the running phase carrying an error`() =
        mainDispatcher.runVmTest {
            speedTest.throwOnMeasure = true
            val vm = viewModel()

            vm.onIntent(SpeedTestIntent.ScreenStarted)
            settle()

            assertEquals(SpeedTestState.Phase.Finished, vm.state.value.phase)
            assertTrue(vm.state.value.error is AppError.Unexpected)
            assertTrue(vm.state.value.canRetry)
        }

    /** A measurement that never completes must still bring the screen out of Running at the bound. */
    @Test
    fun `a measurement that never completes times out`() = mainDispatcher.runVmTest {
        speedTest.emissions = emptyList()
        speedTest.neverCompletes = true
        val vm = viewModel()

        vm.onIntent(SpeedTestIntent.ScreenStarted)
        settle()

        assertEquals(SpeedTestState.Phase.Finished, vm.state.value.phase)
        assertTrue(vm.state.value.error is AppError.Unexpected)
    }

    /** Retry after a failure reaches the repository again. */
    @Test
    fun `retry after a failure runs the measurement again`() = mainDispatcher.runVmTest {
        speedTest.throwOnMeasure = true
        val vm = viewModel()
        vm.onIntent(SpeedTestIntent.ScreenStarted)
        settle()

        speedTest.throwOnMeasure = false
        vm.onIntent(SpeedTestIntent.RetryPressed)
        settle()

        assertEquals(2, speedTest.calls)
        assertNull(vm.state.value.error)
    }

    /** A reported failure arrives as itself, not as a generic Unexpected. */
    @Test
    fun `a reported failure keeps its own error`() = mainDispatcher.runVmTest {
        speedTest.emissions = listOf(SpeedTestProgress.Failed(AppError.NoNetwork))
        val vm = viewModel()

        vm.onIntent(SpeedTestIntent.ScreenStarted)
        settle()

        assertEquals(AppError.NoNetwork, vm.state.value.error)
        assertEquals(SpeedTestState.Phase.Finished, vm.state.value.phase)
    }

    private companion object {
        /**
         * `settle()`'s default horizon is 120 s, which is longer than the ViewModel's own 60 s
         * bound — advancing that far ends the run before the assertion can see it running. A test
         * about the running state advances a fraction of the bound instead.
         */
        const val WhileRunningMillis = 1_000L
    }
}
