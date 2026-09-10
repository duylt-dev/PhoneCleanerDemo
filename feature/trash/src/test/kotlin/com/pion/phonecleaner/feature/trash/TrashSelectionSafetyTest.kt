package com.pion.phonecleaner.feature.trash

import com.pion.phonecleaner.feature.trash.testing.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrashSelectionSafetyTest {
    @get:Rule internal val mainDispatcher = MainDispatcherRule()

    @Test fun `Room removal prunes selected identities and permits selecting the remaining rows`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry("a"), trashEntry("b")))
        TrashFixture(repo).use { f ->
            f.start()
            f.vm.onIntent(TrashIntent.EntryToggled("a"))
            repo.setEntries(persistentListOf(trashEntry("b")))
            runCurrent()
            assertTrue(f.vm.state.value.selectedIds.isEmpty())
            assertFalse(f.vm.state.value.isAllSelected)
            f.vm.onIntent(TrashIntent.SelectAllToggled)
            assertEquals(setOf("b"), f.vm.state.value.selectedIds)
        }
    }

    @Test fun `a dialog freezes targets and cannot be replaced by another action`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry("a"), trashEntry("b")))
        TrashFixture(repo).use { f ->
            f.start()
            f.vm.onIntent(TrashIntent.EntryToggled("a"))
            f.vm.onIntent(TrashIntent.DeleteForeverPressed)
            f.vm.onIntent(TrashIntent.EntryToggled("b"))
            f.vm.onIntent(TrashIntent.SelectAllToggled)
            f.vm.onIntent(TrashIntent.RestorePressed)
            f.vm.onIntent(TrashIntent.EmptyBinPressed)
            assertEquals(TrashAction.DeleteForever, f.vm.state.value.pendingAction)
            assertEquals(1, f.vm.state.value.confirm?.count)
            assertEquals(setOf("a"), f.vm.state.value.pendingIds)
            repo.setEntries(persistentListOf(trashEntry("b"), trashEntry("c")))
            f.vm.onIntent(TrashIntent.ConfirmAccepted)
            assertEquals(listOf("a"), repo.deletedIds)
            assertEquals(0, repo.restoreCalls)
            assertEquals(0, repo.deleteAllCalls)
        }
    }

    @Test fun `permission revoked after opening confirmation prevents the destructive call`() = mainDispatcher.runVmTest {
        TrashFixture(FakeTrashRepository(persistentListOf(trashEntry()))).use { f ->
            f.start()
            f.vm.onIntent(TrashIntent.EmptyBinPressed)
            f.permissions.allFilesGranted = false
            f.vm.onIntent(TrashIntent.ConfirmAccepted)
            assertEquals(0, f.repository.deleteAllCalls)
            assertNull(f.vm.state.value.confirm)
            assertNull(f.vm.state.value.pendingAction)
            assertFalse(f.vm.state.value.isTrashAvailable)
        }
    }

    @Test fun `queued intents while restoring do not reenter or change the pending operation`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry("a"), trashEntry("b"))).apply { suspendRestore = true }
        TrashFixture(repo).use { f ->
            f.start()
            f.restore("a")
            val unrelated = listOf(
                TrashIntent.ScreenStarted, TrashIntent.ConfirmAccepted, TrashIntent.ConfirmDismissed,
                TrashIntent.SelectAllToggled, TrashIntent.EntryToggled("b"), TrashIntent.EmptyBinPressed,
                TrashIntent.DeleteForeverPressed, TrashIntent.RestorePressed,
            )
            repeat(25) { unrelated.shuffled(kotlin.random.Random(it)).forEach(f.vm::onIntent) }
            assertEquals(TrashPhase.Working, f.vm.state.value.phase)
            assertEquals(TrashAction.Restore, f.vm.state.value.pendingAction)
            assertEquals(setOf("a"), f.vm.state.value.selectedIds)
            assertEquals(1, repo.restoreCalls)
            assertEquals(1, repo.reconcileCalls)
            assertEquals(0, repo.deleteCalls)
            assertEquals(0, repo.deleteAllCalls)
        }
    }

    @Test fun `framework clearing cancels a suspended restore without a second action`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry())).apply { suspendRestore = true }
        val fixture = TrashFixture(repo)
        fixture.start()
        fixture.restore()
        fixture.close()
        runCurrent()
        assertTrue(repo.restoreCancelled)
        assertEquals(1, repo.restoreCalls)
    }
}
