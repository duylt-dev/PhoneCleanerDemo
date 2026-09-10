package com.pion.phonecleaner.feature.trash

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.feature.trash.testing.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {
    @get:Rule internal val mainDispatcher = MainDispatcherRule()

    @Test fun `selection toggles only existing ready rows and select all clears again`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry("a"), trashEntry("b")))
        TrashFixture(repo).use { f ->
            f.vm.onIntent(TrashIntent.EntryToggled("a"))
            assertTrue(f.vm.state.value.selectedIds.isEmpty())
            f.start()
            f.vm.onIntent(TrashIntent.EntryToggled("missing"))
            assertTrue(f.vm.state.value.selectedIds.isEmpty())
            f.vm.onIntent(TrashIntent.EntryToggled("a"))
            assertEquals(setOf("a"), f.vm.state.value.selectedIds)
            f.vm.onIntent(TrashIntent.EntryToggled("a"))
            assertTrue(f.vm.state.value.selectedIds.isEmpty())
            f.vm.onIntent(TrashIntent.SelectAllToggled)
            assertEquals(setOf("a", "b"), f.vm.state.value.selectedIds)
            f.vm.onIntent(TrashIntent.SelectAllToggled)
            assertTrue(f.vm.state.value.selectedIds.isEmpty())
        }
    }

    @Test fun `navigation and permission effects are each delivered once`() = mainDispatcher.runVmTest {
        TrashFixture().use { f ->
            f.vm.effects.test {
                f.vm.onIntent(TrashIntent.AllowAccessPressed)
                assertEquals(TrashEffect.RequestAllFilesAccess, awaitItem())
                f.vm.onIntent(TrashIntent.BackPressed)
                assertEquals(TrashEffect.NavigateBack, awaitItem())
                expectNoEvents()
            }
        }
    }

    @Test fun `empty bin uses uncapped operation and confirmation count beyond 500 visible rows`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository((1..501).map { trashEntry("entry-$it") }.toImmutableList())
        TrashFixture(repo).use { f ->
            f.start()
            assertEquals(500, f.vm.state.value.entries.size)
            assertEquals(501, f.vm.state.value.totalEntries)
            f.vm.onIntent(TrashIntent.EmptyBinPressed)
            assertEquals(501, f.vm.state.value.confirm?.count)
            f.vm.onIntent(TrashIntent.ConfirmAccepted)
            runCurrent()
            assertEquals(1, repo.deleteAllCalls)
            assertEquals(0, repo.deleteCalls)
            assertEquals(0, f.vm.state.value.totalEntries)
            assertTrue(f.vm.state.value.entries.isEmpty())
            assertEquals(501_000L, f.ledger.creditedBytes)
        }
    }

    @Test fun `a partial permanent delete reports remaining failures`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry())).apply {
            nextDeleteResult = AppResult.Success(TrashPurgeOutcome(persistentListOf(), 0, persistentListOf("entry-1")))
        }
        TrashFixture(repo).use { f ->
            f.start()
            f.vm.effects.test {
                f.vm.onIntent(TrashIntent.EmptyBinPressed)
                f.vm.onIntent(TrashIntent.ConfirmAccepted)
                assertEquals(TrashEffect.ShowDeleteFailures(1), awaitItem())
                assertEquals(TrashPhase.Ready, f.vm.state.value.phase)
                expectNoEvents()
            }
        }
    }

    @Test fun `restore discloses renamed and failed entries`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry())).apply {
            nextRestoreResult = AppResult.Success(TrashRestoreOutcome(persistentListOf("a"), persistentListOf("b"), 1))
        }
        TrashFixture(repo).use { f ->
            f.start()
            f.vm.effects.test {
                f.restore()
                assertEquals(TrashEffect.ShowRestored(1, 1, 1), awaitItem())
                expectNoEvents()
            }
        }
    }

    @Test fun `resume refreshes expiry clock even without a Room emission`() = mainDispatcher.runVmTest {
        TrashFixture(FakeTrashRepository(persistentListOf(trashEntry()))).use { f ->
            f.start()
            val original = f.vm.state.value.now
            f.clock.now += 1.days
            f.vm.onIntent(TrashIntent.ScreenResumed)
            assertEquals(original + 1.days, f.vm.state.value.now)
            assertEquals(ExpiryLabel.DaysLeft(1), f.vm.state.value.entries.single().expiryLabel(f.vm.state.value.now))
        }
    }

    @Test fun `new collector replays confirmation without repeating an operation`() = mainDispatcher.runVmTest {
        TrashFixture(FakeTrashRepository(persistentListOf(trashEntry()))).use { f ->
            f.start()
            f.vm.onIntent(TrashIntent.EntryToggled("entry-1"))
            f.vm.onIntent(TrashIntent.RestorePressed)
            val confirmation = assertNotNull(f.vm.state.value.confirm)
            f.vm.state.test {
                assertEquals(confirmation, awaitItem().confirm)
                cancelAndIgnoreRemainingEvents()
            }
            f.vm.state.test {
                assertEquals(confirmation, awaitItem().confirm)
                assertEquals(0, f.repository.restoreCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
