package com.pion.phonecleaner.feature.antivirus.result

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.security.RiskLevel
import com.pion.phonecleaner.domain.usecase.IgnoreFindingUseCase
import com.pion.phonecleaner.domain.usecase.RemoveFindingUseCase
import com.pion.phonecleaner.feature.antivirus.testing.FakeFileDeleter
import com.pion.phonecleaner.feature.antivirus.testing.FakeSecurityScanRepository
import com.pion.phonecleaner.feature.antivirus.testing.MainDispatcherRule
import com.pion.phonecleaner.feature.antivirus.testing.runVmTest
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
 * The screen the competitor writes with no ViewModel at all. Every test below names a defect from
 * `docs/screens/15-antivirus.md` §2.5.
 */
internal class AntivirusResultViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val deleter = FakeFileDeleter()

    private fun viewModel(
        repository: FakeSecurityScanRepository,
        expectedCount: Int = 0,
    ) = AntivirusResultViewModel(
        savedState = SavedStateHandle(mapOf("findingCount" to expectedCount)),
        repository = repository,
        removeFinding = RemoveFindingUseCase(repository, deleter),
        ignoreFinding = IgnoreFindingUseCase(repository),
        log = AppLogger.NoOp,
    )

    private fun started(
        repository: FakeSecurityScanRepository,
        expectedCount: Int = 0,
    ) = viewModel(repository, expectedCount).also {
        it.onIntent(AntivirusResultIntent.ScreenStarted)
    }

    // ── the read path ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `the route argument seeds the headline and the first emission overwrites it`() =
        main.runVmTest {
            val repository = FakeSecurityScanRepository()
            val vm = viewModel(repository, expectedCount = 3)

            assertEquals(3, vm.state.value.findingCount)

            vm.onIntent(AntivirusResultIntent.ScreenStarted)
            repository.emitRecord(listOf(verdict("aa")))

            assertEquals(1, vm.state.value.findingCount)
            assertFalse(vm.state.value.isLoading)
        }

    @Test
    fun `no record at all is a different state from a check that found nothing`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = started(repository)

        // What this build reaches: the SDK has no Maven coordinate, so no scan ever completes.
        assertTrue(vm.state.value.hasNeverScanned)
        assertTrue(vm.state.value.isEmpty)

        repository.emitRecord(emptyList())
        assertFalse(vm.state.value.hasNeverScanned)
        assertTrue(vm.state.value.isEmpty)
    }

    @Test
    fun `the headline level comes from the rows, not from a hard-coded colour`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = started(repository)

        repository.emitRecord(listOf(verdict("aa", score = 6)))
        assertEquals(RiskLevel.Elevated, vm.state.value.headlineLevel)

        repository.emitRecord(listOf(verdict("aa", score = 6), verdict("bb", score = 8)))
        assertEquals(RiskLevel.High, vm.state.value.headlineLevel)
        assertEquals(1, vm.state.value.highCount)
        assertEquals(1, vm.state.value.elevatedCount)
    }

    // ── removal ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `an installed app raises the uninstall Effect and greys only its own row`() =
        main.runVmTest {
            val repository = FakeSecurityScanRepository()
            val vm = started(repository)
            repository.emitRecord(listOf(verdict("aa", packageName = "com.x"), verdict("bb")))

            vm.onIntent(AntivirusResultIntent.FindingTapped("aa"))
            assertEquals("aa", vm.state.value.pendingRemovalMd5)

            vm.effects.test {
                vm.onIntent(AntivirusResultIntent.RemovalConfirmed)
                assertEquals(
                    AntivirusResultEffect.LaunchUninstall("aa", "com.x"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }

            // Nothing has been removed yet — the system dialog does that — so the row stays, greyed.
            assertNull(vm.state.value.pendingRemovalMd5)
            assertTrue("aa" in vm.state.value.removingMd5s)
            assertFalse("bb" in vm.state.value.removingMd5s)
            assertTrue(repository.forgotten.isEmpty())
        }

    @Test
    fun `a second tap on a row already being removed opens nothing`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = started(repository)
        repository.emitRecord(listOf(verdict("aa", packageName = "com.x")))
        vm.onIntent(AntivirusResultIntent.FindingTapped("aa"))
        vm.onIntent(AntivirusResultIntent.RemovalConfirmed)

        vm.onIntent(AntivirusResultIntent.FindingTapped("aa"))

        assertNull(vm.state.value.pendingRemovalMd5)
    }

    @Test
    fun `a loose file is deleted here and the row is forgotten`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        deleter.outcome = AppResult.Success(
            DeleteOutcome.Deleted(persistentListOf("aa"), 1_024L, persistentListOf()),
        )
        val vm = started(repository)
        repository.emitRecord(listOf(verdict("aa")))

        vm.onIntent(AntivirusResultIntent.FindingTapped("aa"))
        vm.onIntent(AntivirusResultIntent.RemovalConfirmed)

        assertEquals(listOf("aa"), repository.forgotten)
        assertTrue(vm.state.value.findings.isEmpty())
        assertTrue(vm.state.value.removingMd5s.isEmpty())
    }

    @Test
    fun `a failed removal lowers the flag it raised and reports`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        deleter.outcome = AppResult.Failure(storageFailure)
        val vm = started(repository)
        repository.emitRecord(listOf(verdict("aa")))
        vm.onIntent(AntivirusResultIntent.FindingTapped("aa"))

        vm.effects.test {
            vm.onIntent(AntivirusResultIntent.RemovalConfirmed)
            assertEquals(AntivirusResultEffect.ShowMessage(storageFailure), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        // MVI §1: onError lowers every flag the call raised, or the row is greyed for ever.
        assertTrue(vm.state.value.removingMd5s.isEmpty())
        assertEquals(1, vm.state.value.findings.size)
    }

    // ── the broadcast ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a removal broadcast is matched against the list`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = started(repository)
        repository.emitRecord(
            listOf(verdict("aa", packageName = "com.x"), verdict("bb", packageName = "com.y")),
        )

        vm.onIntent(AntivirusResultIntent.PackageRemoved("com.y"))

        assertEquals(listOf("bb"), repository.forgotten)
        assertEquals(listOf("aa"), vm.state.value.findings.map { it.md5 })
    }

    @Test
    fun `an unmatched broadcast removes nothing`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = started(repository)
        repository.emitRecord(listOf(verdict("aa", packageName = "com.x")))

        // Uninstalling any app elsewhere currently deletes the competitor's last-tapped row (§2.5).
        vm.onIntent(AntivirusResultIntent.PackageRemoved("com.somethingelse"))

        assertTrue(repository.forgotten.isEmpty())
        assertEquals(1, vm.state.value.findings.size)
    }

    @Test
    fun `returning from a cancelled uninstall un-greys the row`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = started(repository)
        repository.emitRecord(listOf(verdict("aa", packageName = "com.x")))
        vm.onIntent(AntivirusResultIntent.FindingTapped("aa"))
        vm.onIntent(AntivirusResultIntent.RemovalConfirmed)
        assertTrue("aa" in vm.state.value.removingMd5s)

        vm.onIntent(AntivirusResultIntent.UninstallReturned("aa"))

        // Without this the row stays greyed for ever — a state the competitor cannot even reach,
        // because it fires the uninstall with startActivity and never learns the outcome.
        assertTrue(vm.state.value.removingMd5s.isEmpty())
        assertEquals(1, vm.state.value.findings.size)
    }

    // ── the additive action, and navigation ────────────────────────────────────────────────────

    @Test
    fun `ignoring a finding writes to the ignore list and the row leaves`() = main.runVmTest {
        val repository = FakeSecurityScanRepository()
        val vm = started(repository)
        repository.emitRecord(listOf(verdict("aa"), verdict("bb")))

        vm.onIntent(AntivirusResultIntent.IgnorePressed("aa"))

        assertEquals(listOf("aa"), repository.ignored)
        assertEquals(listOf("bb"), vm.state.value.findings.map { it.md5 })
    }

    @Test
    fun `rescan and back are Effects, never flags on state`() = main.runVmTest {
        val vm = started(FakeSecurityScanRepository())

        vm.effects.test {
            vm.onIntent(AntivirusResultIntent.RescanPressed)
            assertEquals(AntivirusResultEffect.NavigateToScan, awaitItem())
            vm.onIntent(AntivirusResultIntent.BackPressed)
            assertEquals(AntivirusResultEffect.NavigateBack, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
