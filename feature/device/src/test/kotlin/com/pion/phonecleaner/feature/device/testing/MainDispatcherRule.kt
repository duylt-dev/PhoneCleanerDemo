package com.pion.phonecleaner.feature.device.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * `viewModelScope` is hard-wired to `Dispatchers.Main.immediate`, which does not exist on a plain
 * JVM runtime (`LLM.md` §9). Copied from `feature/files/testing/MainDispatcherRule.kt`, which is the
 * pattern for this codebase.
 *
 * **Unconfined**, so a `launch` raised inside `init` has already run by the time the constructor
 * returns — which is what the screen sees too. One scheduler is shared with `runTest`, so
 * `advanceTimeBy` moves the scan timelines this cluster is built out of.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class MainDispatcherRule(
    val scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    val dispatcher: TestDispatcher = UnconfinedTestDispatcher(scheduler),
) : TestWatcher() {

    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)

    override fun finished(description: Description) = Dispatchers.resetMain()
}

/** Runs on the rule's dispatcher, so the ViewModel and the test share one virtual clock. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun MainDispatcherRule.runVmTest(body: suspend TestScope.() -> Unit) =
    runTest(dispatcher) { body() }

/**
 * A **bounded** advance. `advanceUntilIdle` on a ViewModel holding a ticker never returns, and every
 * scan screen in this cluster holds one (`LLM.md` §9).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.settle(horizonMillis: Long = 120_000L) {
    testScheduler.advanceTimeBy(horizonMillis)
    testScheduler.runCurrent()
}
