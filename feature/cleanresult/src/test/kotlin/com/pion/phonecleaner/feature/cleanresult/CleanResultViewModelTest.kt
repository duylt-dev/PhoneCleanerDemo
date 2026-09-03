package com.pion.phonecleaner.feature.cleanresult

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.CleanupLedger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Hand-written fakes, no mocking library (`LLM.md` §9).
 *
 * The fakes are down to one. `FeatureUsageRepository` and `AnalyticsRepository` left this ViewModel
 * with the suggestion list (owner decision 2026-09-03), and their fakes left with them rather than
 * standing as unused test scaffolding that reads like a dependency this screen still has.
 *
 * `viewModelScope` is hard-wired to `Dispatchers.Main.immediate`, which does not exist on a plain
 * JVM runtime, so `Dispatchers.setMain` is not optional here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class CleanResultViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val ledger = FakeCleanupLedger()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(summary: CleanupSummary = cleaned()) = CleanResultViewModel(
        summary = summary,
        ledger = ledger,
        log = AppLogger.NoOp,
    )

    /** The lifetime total is READ from the ledger, never re-parsed from a formatted string (§8). */
    @Test
    fun `the lifetime total comes from the ledger as a raw Long`() = runTest(dispatcher) {
        ledger.lifetime.value = 12_345L

        val vm = viewModel()

        assertEquals(12_345L, vm.state.value.lifetimeFreedBytes)
        assertEquals(4_096L, vm.state.value.freedBytes)
    }

    /** The screen opens counting and is revealed by the animation, not by an ad SDK callback. */
    @Test
    fun `the reveal is driven by the animation intent`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(ResultPhase.Counting, vm.state.value.phase)
        assertFalse(vm.state.value.isRevealed)

        vm.onIntent(CleanResultIntent.CountingAnimationFinished)

        assertTrue(vm.state.value.isRevealed)
    }

    /** An outcome with nothing to report says so instead of rendering "0 B" as a success. */
    @Test
    fun `a run that found nothing does not present a byte figure`() = runTest(dispatcher) {
        val vm = viewModel(
            CleanupSummary(
                feature = FeatureId.JunkClean,
                freedBytes = 0L,
                itemCount = 0,
                outcome = CleanupOutcome.NothingFound,
            ),
        )

        assertFalse(vm.state.value.showsBytes)
    }

    private fun cleaned(feature: FeatureId = FeatureId.DuplicateFiles) = CleanupSummary(
        feature = feature,
        freedBytes = 4_096L,
        itemCount = 2,
        outcome = CleanupOutcome.Cleaned,
    )
}

internal class FakeCleanupLedger : CleanupLedger {
    val lifetime = MutableStateFlow(0L)
    override suspend fun record(freedBytes: Long): AppResult<Unit> {
        lifetime.value += freedBytes
        return AppResult.Success(Unit)
    }

    override fun observeLifetimeFreedBytes(): Flow<Long> = lifetime
}
