package com.pion.phonecleaner.feature.antivirus.scan

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.security.ScanConsentState
import com.pion.phonecleaner.domain.model.security.ScanCoverage
import com.pion.phonecleaner.domain.model.security.ScanFailure
import com.pion.phonecleaner.domain.model.security.SecurityScanPhase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.feature.antivirus.testing.FakeFeatureUsageRepository
import com.pion.phonecleaner.feature.antivirus.testing.FakeSecurityScanRepository
import com.pion.phonecleaner.feature.antivirus.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.antivirus.testing.runVmTest
import com.pion.phonecleaner.feature.antivirus.testing.settle
import com.pion.phonecleaner.feature.antivirus.testing.storageFailure
import com.pion.phonecleaner.feature.antivirus.testing.verdict
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every gate, every terminal phase and the stop path — the state machine the competitor cannot test
 * at all, because its equivalent sealed class *drives* a five-minute animation job
 * (`docs/reverse-engineering/15-antivirus.md` §3.2).
 */
internal class AntivirusScanViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val usage = FakeFeatureUsageRepository()

    private fun viewModel(repository: FakeSecurityScanRepository) = AntivirusScanViewModel(
        repository = repository,
        markFeatureUsed = MarkFeatureUsedUseCase(usage),
        log = AppLogger.NoOp,
    )

    private fun allClear(vm: AntivirusScanViewModel) = vm.onIntent(
        AntivirusScanIntent.GateStateReported(hasStorageAccess = true, isOnline = true),
    )

    // ── the three gates ────────────────────────────────────────────────────────────────────────

    @Test
    fun `nothing starts before the composable has reported platform state`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = viewModel(repository)

        vm.onIntent(AntivirusScanIntent.ScreenStarted)

        assertEquals(SecurityScanPhase.Idle, vm.state.value.phase)
        assertFalse(repository.isScanCollected)
        assertEquals(listOf(FeatureId.Antivirus), usage.marked)
    }

    @Test
    fun `offline blocks on the network gate and starts no scan`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = viewModel(repository)

        vm.onIntent(
            AntivirusScanIntent.GateStateReported(hasStorageAccess = true, isOnline = false),
        )

        assertEquals(ScanGate.Network, vm.state.value.blockingGate)
        assertFalse(repository.isScanCollected)
    }

    @Test
    fun `an unanswered disclosure opens the dialog instead of scanning`() = main.runVmTest {
        val repository = FakeSecurityScanRepository(consent = ScanConsentState.Unanswered)
        val vm = viewModel(repository)

        allClear(vm)

        assertTrue(vm.state.value.isConsentDialogVisible)
        assertEquals(ScanGate.Consent, vm.state.value.blockingGate)
        assertFalse(repository.isScanCollected)
    }

    @Test
    fun `a rejection is recorded, so it is remembered rather than re-asked`() = main.runVmTest {
        val repository = FakeSecurityScanRepository(consent = ScanConsentState.Unanswered)
        val vm = viewModel(repository)
        allClear(vm)

        vm.onIntent(AntivirusScanIntent.ConsentRejected)

        // The competitor stores a single string and only ever writes it on acceptance (§0.5).
        assertEquals(listOf(false), repository.consentWrites)
        assertFalse(vm.state.value.isConsentDialogVisible)
        assertEquals(ScanGate.Consent, vm.state.value.blockingGate)

        // Re-entering the screen does not re-open the dialog.
        allClear(vm)
        assertFalse(vm.state.value.isConsentDialogVisible)
    }

    @Test
    fun `the storage gate asks once and then runs with partial coverage`() = main.runVmTest {
        val repository = FakeSecurityScanRepository(scanNeverCompletes = true)
        val vm = viewModel(repository)

        vm.onIntent(
            AntivirusScanIntent.GateStateReported(hasStorageAccess = false, isOnline = true),
        )
        assertEquals(ScanGate.StoragePermission, vm.state.value.blockingGate)

        vm.effects.test {
            vm.onIntent(AntivirusScanIntent.GrantStoragePressed)
            assertEquals(AntivirusScanEffect.RequestStorageAccess, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // The user declined. The gate does NOT come back: the scan runs over what it can reach and
        // the screen says what was skipped (§1.5, system-architecture.md §8.4).
        vm.onIntent(
            AntivirusScanIntent.GateStateReported(hasStorageAccess = false, isOnline = true),
        )

        assertNull(vm.state.value.blockingGate)
        assertEquals(ScanCoverage.InstalledAppsOnly, vm.state.value.coverage)
        assertTrue(vm.state.value.coverage.isPartial)
        assertTrue(repository.isScanCollected)
    }

    // ── the terminal phases ────────────────────────────────────────────────────────────────────

    @Test
    fun `findings are persisted before the navigation Effect fires`() = main.runVmTest {
        val findings = persistentListOf(verdict("aa"), verdict("bb", packageName = "com.x"))
        val repository = FakeSecurityScanRepository(
            scanPhases = listOf(SecurityScanPhase.Finished(findings)),
        )
        val vm = viewModel(repository)

        vm.effects.test {
            allClear(vm)
            // The write has already happened when the effect is read: the result screen has no
            // list-bearing argument, so an unwritten record is an empty screen (§0.4).
            assertEquals(findings.toList(), repository.recorded.single())
            assertEquals(AntivirusScanEffect.NavigateToResult(2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `zero findings is a clean result, not an error`() = main.runVmTest {
        val repository = FakeSecurityScanRepository(
            scanPhases = listOf(SecurityScanPhase.Finished(persistentListOf())),
        )
        val vm = viewModel(repository)

        vm.effects.test {
            allClear(vm)
            val effect = awaitItem()
            assertTrue(effect is AntivirusScanEffect.NavigateToCleanResult)
            assertEquals(CleanupOutcome.NothingFound, effect.summary.outcome)
            assertEquals(0L, effect.summary.freedBytes)
            assertEquals(FeatureId.Antivirus, effect.summary.feature)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed write is not a navigable outcome`() = main.runVmTest {
        val repository = FakeSecurityScanRepository(
            scanPhases = listOf(SecurityScanPhase.Finished(persistentListOf(verdict("aa")))),
            recordFinishedResult = AppResult.Failure(storageFailure),
        )
        val vm = viewModel(repository)

        vm.effects.test {
            allClear(vm)
            assertEquals(AntivirusScanEffect.ShowMessage(storageFailure), awaitItem())
            expectNoEvents()
        }
        assertEquals(SecurityScanPhase.Failed(ScanFailure.Unknown), vm.state.value.phase)
    }

    @Test
    fun `the sdk-unavailable failure is a rendered state and offers no retry`() = main.runVmTest {
        // What this build actually does: TrustLookClient has no SDK on the classpath.
        val repository = FakeSecurityScanRepository(
            scanPhases = listOf(SecurityScanPhase.Failed(ScanFailure.SdkUnavailable)),
        )
        val vm = viewModel(repository)

        vm.effects.test {
            allClear(vm)
            expectNoEvents()
        }
        assertEquals(ScanFailure.SdkUnavailable, vm.state.value.failure)
        assertFalse(vm.state.value.failure!!.isRetryable)
    }

    @Test
    fun `the budget wraps the collection, and the timeout cancels the client`() = main.runVmTest {
        val repository = FakeSecurityScanRepository(
            scanPhases = listOf(SecurityScanPhase.Preparing),
            scanNeverCompletes = true,
        )
        val vm = viewModel(repository)
        allClear(vm)
        assertTrue(repository.isScanCollected)

        settle(SCAN_BUDGET_HORIZON_MS)

        assertEquals(SecurityScanPhase.Failed(ScanFailure.Timeout), vm.state.value.phase)
        // `awaitClose` ran: cancelling the collector cancels the client, which is the whole
        // cancellation story the competitor needs three call sites for (§0.3).
        assertFalse(repository.isScanCollected)
    }

    // ── stopping ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `back while running raises the stop confirm, and stopping cancels and leaves`() =
        main.runVmTest {
            val repository = FakeSecurityScanRepository(
                scanPhases = listOf(SecurityScanPhase.Scanning(1, 4, "one")),
                scanNeverCompletes = true,
            )
            val vm = viewModel(repository)
            allClear(vm)
            assertTrue(vm.state.value.isRunning)

            vm.onIntent(AntivirusScanIntent.BackPressed)
            assertTrue(vm.state.value.isStopConfirmVisible)

            vm.effects.test {
                vm.onIntent(AntivirusScanIntent.StopConfirmed)
                assertEquals(AntivirusScanEffect.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(SecurityScanPhase.Idle, vm.state.value.phase)
            assertFalse(vm.state.value.isStopConfirmVisible)
            assertFalse(repository.isScanCollected)
        }

    @Test
    fun `back while idle leaves immediately`() = main.runVmTest {
        val vm = viewModel(FakeSecurityScanRepository())

        vm.effects.test {
            vm.onIntent(AntivirusScanIntent.BackPressed)
            assertEquals(AntivirusScanEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the elapsed counter runs while scanning and stops with the scan`() = main.runVmTest {
        val repository = FakeSecurityScanRepository(
            scanPhases = listOf(SecurityScanPhase.Scanning(1, 2, "one")),
            scanNeverCompletes = true,
        )
        val vm = viewModel(repository)
        allClear(vm)

        settle(3_000L)
        assertTrue(vm.state.value.elapsedMs > 0L)

        vm.onIntent(AntivirusScanIntent.StopConfirmed)
        val stopped = vm.state.value.elapsedMs
        settle(3_000L)
        // The ticker is a child of the scan job, so cancelling the job stops it. Without the
        // `finally` in `startScan` it would keep writing state after the scan had ended.
        assertEquals(stopped, vm.state.value.elapsedMs)
    }

    private companion object {
        /** The ViewModel's own five-minute budget, plus a tick to land past the edge. */
        const val SCAN_BUDGET_HORIZON_MS = 5 * 60 * 1_000L + 1_000L
    }
}
