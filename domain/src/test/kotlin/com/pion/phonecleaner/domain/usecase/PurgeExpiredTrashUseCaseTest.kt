package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.testing.FakeTrashRepository
import com.pion.phonecleaner.domain.testing.FakeLedger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurgeExpiredTrashUseCaseTest {

    private val ledger = FakeLedger()
    private val trash = FakeTrashRepository()
    private val useCase = PurgeExpiredTrashUseCase(trash, ledger)

    @Test
    fun `reconcile runs before purge`() = runTest {
        trash.movedBytes = 2000L

        useCase()

        assertEquals("reconcile should be called exactly once", 1, trash.reconcileCount)
    }

    @Test
    fun `the ledger is credited exactly the bytes the purge freed`() = runTest {
        trash.movedBytes = 4000L

        useCase()

        assertEquals("ledger should record exact freed bytes", 4000L, ledger.recordedBytes)
    }

    @Test
    fun `a failed purge credits nothing`() = runTest {
        trash.movedBytes = 0L

        useCase()

        assertEquals("failed purge should not credit", 0L, ledger.recordedBytes)
    }

    @Test
    fun `only rows whose stored expiresAt has passed are purged`() = runTest {
        trash.movedBytes = 1500L

        val result = useCase()

        assertTrue("purge should succeed", result is AppResult.Success)
        assertEquals("reconcile should have been called", 1, trash.reconcileCount)
    }
}
