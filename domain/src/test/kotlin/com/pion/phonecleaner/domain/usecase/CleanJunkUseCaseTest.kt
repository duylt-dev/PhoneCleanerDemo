package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.testing.FakeJunkRepository
import com.pion.phonecleaner.domain.testing.FakeTrashRepository
import com.pion.phonecleaner.domain.testing.FakeLedger
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CleanJunkUseCaseTest {

    private val junkPath = "/data/app-cache/junk"
    private val ledger = FakeLedger()
    private val junkRepo = FakeJunkRepository()
    private val trash = FakeTrashRepository()

    private val useCase = CleanJunkUseCase(junkRepo, trash, ledger)

    @Test
    fun `with the bin available the deleter is never called`() = runTest {
        trash.isAvailable = true
        trash.movedBytes = 5000L

        useCase(setOf(junkPath), requireTrash = trash.isAvailable).toList()

        assertTrue("junk deleter should not be called when bin is available", junkRepo.cleanCalls.isEmpty())
    }

    @Test
    fun `with the bin available the ledger is never written`() = runTest {
        trash.isAvailable = true
        trash.movedBytes = 5000L

        useCase(setOf(junkPath), requireTrash = trash.isAvailable).toList()

        assertEquals("ledger should not record on trash branch", 0L, ledger.recordedBytes)
    }

    @Test
    fun `with the bin available the outcome is recoverable`() = runTest {
        trash.isAvailable = true
        trash.movedBytes = 5000L

        val emissions = useCase(setOf(junkPath), requireTrash = trash.isAvailable).toList()

        val finished = emissions.filterIsInstance<CleanProgress.Finished>().lastOrNull()
        assertTrue("outcome should be recoverable when moved to trash", finished?.outcome?.recoverable == true)
    }

    @Test
    fun `with the bin unavailable the behaviour is byte-for-byte what it was`() = runTest {
        trash.isAvailable = false

        useCase(setOf(junkPath), requireTrash = trash.isAvailable).toList()

        assertEquals("junk deleter should be called when bin unavailable", 1, junkRepo.cleanCalls.size)
        assertEquals("ledger should record freed bytes on permanent delete", 5000L, ledger.recordedBytes)
    }
    @Test
    fun `confirmed trash never falls back to permanent deletion when bin becomes unavailable`() = runTest {
        trash.isAvailable = false
        val result = useCase(setOf(junkPath), requireTrash = true).toList()
        assertTrue(result.any { it is CleanProgress.Failed })
        assertTrue(junkRepo.cleanCalls.isEmpty())
        assertEquals(0L, ledger.recordedBytes)
    }
}
