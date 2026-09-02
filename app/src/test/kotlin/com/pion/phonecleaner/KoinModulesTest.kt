package com.pion.phonecleaner

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.KoinTest
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.time.Duration

/**
 * The DI gate from LLM.md §10.5: a missing binding fails the build instead of the screen that opens.
 *
 * The competitor has no DI at all, so nothing there catches one, ever — every wiring mistake is a
 * runtime crash on a user's device (`r2-05-file-tools-appmanager.md:3907`).
 *
 * **`verifyAll`, not `forEach { verify() }`.** Per-module verification resolves only against that one
 * module, so it reports every legitimate cross-module dependency as missing — `domainModule`'s use
 * cases take repositories that `coreDataModule` owns, which is the whole point of §6.4's
 * one-declaring-module-per-type rule. Verifying them apart would force each module to re-declare what
 * it consumes, which is exactly the duplicate-binding defect the rule exists to prevent.
 */
class KoinModulesTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `the whole module graph resolves every dependency it declares`() {
        // extraTypes lists what verification must NOT try to resolve from the graph:
        //   Context  — supplied by androidContext() at startKoin, not by a module.
        //   Duration — MinimumDuration's `floor` is passed as a literal inside coreModule's lambda.
        //              A `single<Duration>` would be a nameless global constant pretending to be a
        //              dependency; the value belongs at its one call site.
        //   SavedStateHandle — reaches a `viewModel { params -> X(params.get(), ...) }` from the nav
        //              back-stack entry at call time, exactly as Context reaches androidContext().
        //              Every route-argument ViewModel trips this; none of them can resolve it from
        //              the graph, and none should.
        //   CleanupSummary — the clean-result route's own argument, passed the same way. It is a
        //              value object carried by a navigation call, never a service: a
        //              `single<CleanupSummary>` would be a process-wide shared result, which is the
        //              exact bug the competitor's three `volatile` statics caused.
        val graph = module { includes(appModules) }
        graph.verify(extraTypes = listOf(Context::class, Duration::class, SavedStateHandle::class, CleanupSummary::class))
    }
}
