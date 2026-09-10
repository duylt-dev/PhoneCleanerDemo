package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.testing.FakeTrashRepository
import com.pion.phonecleaner.domain.testing.FakeLedger
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertTrue
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteTrashForeverUseCaseTest {

    private val ledger = FakeLedger()
    private val trash = FakeTrashRepository()
    private val useCase = DeleteTrashForeverUseCase(trash, ledger)

    @Test
    fun `the ledger is credited exactly the bytes the purge freed`() = runTest {
        trash.movedBytes = 5000L

        useCase(listOf("id-1", "id-2"))

        assertEquals("ledger should record exact freed bytes", 5000L, ledger.recordedBytes)
    }

    @Test
    fun `a failed purge credits nothing`() = runTest {
        val failing = object : TrashRepository by trash {
            override suspend fun deleteForever(ids: List<String>): AppResult<TrashPurgeOutcome> =
                AppResult.Failure(AppError.Storage())
        }
        val result = DeleteTrashForeverUseCase(failing, ledger)(listOf("id-1"))
        assertTrue(result is AppResult.Failure)

        assertEquals("failed purge should not credit", 0L, ledger.recordedBytes)
    }

    @Test
    fun `deleteForever is called with the provided ids`() = runTest {
        trash.movedBytes = 3000L

        useCase(listOf("id-1", "id-2", "id-3"))

        assertEquals("all ids should be purged", 3, trash.purgedIds.size)
        assertEquals("id-1 should be purged", "id-1", trash.purgedIds[0])
        assertEquals("id-2 should be purged", "id-2", trash.purgedIds[1])
        assertEquals("id-3 should be purged", "id-3", trash.purgedIds[2])
    }
    @Test
    fun `empty all calls the uncapped operation and credits its result`() = runTest {
        var allCalls = 0
        val allBin = object : TrashRepository by trash {
            override suspend fun deleteAllForever(): AppResult<TrashPurgeOutcome> {
                allCalls++
                return AppResult.Success(TrashPurgeOutcome(
                    (1..501).map { "id-$it" }.toImmutableList(), 501L, emptyList<String>().toImmutableList(),
                ))
            }
        }
        val result = DeleteTrashForeverUseCase(allBin, ledger).all() as AppResult.Success
        assertEquals(501, result.value.purgedIds.size)
        assertEquals(501L, ledger.recordedBytes)
        assertEquals(1, allCalls)
        assertTrue(trash.purgedIds.isEmpty())
    }

    @Test
    fun `cancelling the caller after deletion starts still credits the settled batch`() = runTest {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val delayed = object : TrashRepository by trash {
            override suspend fun deleteForever(ids: List<String>): AppResult<TrashPurgeOutcome> {
                started.complete(Unit)
                finish.await()
                return AppResult.Success(TrashPurgeOutcome(ids.toImmutableList(), 50L,
                    emptyList<String>().toImmutableList()))
            }
        }
        val job = launch { DeleteTrashForeverUseCase(delayed, ledger)(listOf("id-1")) }
        started.await()
        job.cancel()
        runCurrent()
        assertEquals(0L, ledger.recordedBytes)
        finish.complete(Unit)
        job.join()
        assertEquals(50L, ledger.recordedBytes)
    }
}
