package com.pion.phonecleaner.feature.trash

import app.cash.turbine.test
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.feature.trash.testing.*
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TrashRecoveryTest {
    @get:Rule internal val mainDispatcher = MainDispatcherRule()

    @Test fun `a suspended restore times out cancels its child and permits a real retry`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry())).apply { suspendRestore = true }
        TrashFixture(repo).use { f ->
            f.start()
            f.vm.effects.test {
                f.restore()
                assertEquals(TrashPhase.Working, f.vm.state.value.phase)
                advanceTimeBy(ACTION_TIMEOUT_MILLIS - 1)
                runCurrent()
                assertEquals(TrashPhase.Working, f.vm.state.value.phase)
                expectNoEvents()
                advanceTimeBy(1)
                runCurrent()
                assertIs<AppError.Storage>(assertIs<TrashEffect.ShowMessage>(awaitItem()).error)
                assertTrue(repo.restoreCancelled)
                assertEquals(TrashPhase.Ready, f.vm.state.value.phase)
                assertNull(f.vm.state.value.pendingAction)
                repo.suspendRestore = false
                f.vm.onIntent(TrashIntent.RestorePressed)
                f.vm.onIntent(TrashIntent.ConfirmAccepted)
                assertIs<TrashEffect.ShowRestored>(awaitItem())
                assertEquals(2, repo.restoreCalls)
                expectNoEvents()
            }
        }
    }

    @Test fun `a suspended reconcile is single flight and timeout permits retry`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository().apply { suspendReconcile = true }
        TrashFixture(repo).use { f ->
            f.start()
            repeat(5) { f.vm.onIntent(TrashIntent.ScreenStarted) }
            assertEquals(1, repo.reconcileCalls)
            assertTrue(f.vm.state.value.isReconciling)
            advanceTimeBy(RECONCILE_TIMEOUT_MILLIS)
            runCurrent()
            assertTrue(repo.reconcileCancelled)
            assertFalse(f.vm.state.value.isReconciling)
            assertIs<AppError.Storage>(f.vm.state.value.error)
            repo.suspendReconcile = false
            f.vm.onIntent(TrashIntent.ScreenStarted)
            assertEquals(2, repo.reconcileCalls)
            assertEquals(TrashPhase.Ready, f.vm.state.value.phase)
            assertNull(f.vm.state.value.error)
        }
    }

    @Test fun `a thrown reconcile failure is contained and the retry reaches the repository`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository().apply { reconcileException = IllegalStateException("broken DB") }
        TrashFixture(repo).use { f ->
            f.start()
            assertIs<AppError.Unexpected>(f.vm.state.value.error)
            assertFalse(f.vm.state.value.isReconciling)
            repo.reconcileException = null
            f.vm.onIntent(TrashIntent.ScreenStarted)
            assertEquals(2, repo.reconcileCalls)
            assertNull(f.vm.state.value.error)
        }
    }

    @Test fun `a thrown restore failure lowers the guards and retry reaches the repository`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry())).apply {
            restoreException = IllegalStateException("restore failed")
        }
        TrashFixture(repo).use { f ->
            f.start()
            f.vm.effects.test {
                f.restore()
                assertIs<AppError.Unexpected>(assertIs<TrashEffect.ShowMessage>(awaitItem()).error)
                assertEquals(TrashPhase.Ready, f.vm.state.value.phase)
                assertNull(f.vm.state.value.confirm)
                assertNull(f.vm.state.value.pendingAction)
                assertTrue("entry-1" in f.vm.state.value.selectedIds)
                repo.restoreException = null
                f.vm.onIntent(TrashIntent.RestorePressed)
                f.vm.onIntent(TrashIntent.ConfirmAccepted)
                assertIs<TrashEffect.ShowRestored>(awaitItem())
                assertEquals(2, repo.restoreCalls)
            }
        }
    }

    @Test fun `a thrown permanent-delete failure permits retry of the same selection`() = mainDispatcher.runVmTest {
        val repo = FakeTrashRepository(persistentListOf(trashEntry())).apply { deleteException = IllegalStateException("write failed") }
        TrashFixture(repo).use { f ->
            f.start()
            f.vm.onIntent(TrashIntent.EntryToggled("entry-1"))
            f.vm.effects.test {
                f.vm.onIntent(TrashIntent.DeleteForeverPressed)
                f.vm.onIntent(TrashIntent.ConfirmAccepted)
                assertIs<TrashEffect.ShowMessage>(awaitItem())
                assertEquals(TrashPhase.Ready, f.vm.state.value.phase)
                repo.deleteException = null
                f.vm.onIntent(TrashIntent.DeleteForeverPressed)
                f.vm.onIntent(TrashIntent.ConfirmAccepted)
                assertEquals(2, repo.deleteCalls)
                assertEquals(listOf("entry-1"), repo.deletedIds)
                assertFalse(f.vm.state.value.canAct)
                expectNoEvents()
            }
        }
    }
}
