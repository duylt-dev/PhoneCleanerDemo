package com.pion.phonecleaner.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.trash.TrashFixture
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.TrashRepository
import com.pion.phonecleaner.domain.usecase.PurgeExpiredTrashUseCase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TrashPurgeWorkerTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun `successful purge returns success and credits actual result`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        var calls = 0
        var bytes = 0L
        val repository = object : TrashRepository by f.repository {
            override suspend fun purgeExpired(): AppResult<TrashPurgeOutcome> {
                calls++
                return AppResult.Success(TrashPurgeOutcome(persistentListOf("removed"), 17L, persistentListOf()))
            }
        }
        val worker = worker(f.context, repository) { bytes += it }
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(1, calls)
        assertEquals(17L, bytes)
    }

    @Test fun `failed or throwing purge returns success without recording bytes`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        var shouldThrow = false
        val repository = object : TrashRepository by f.repository {
            override suspend fun purgeExpired(): AppResult<TrashPurgeOutcome> {
                if (shouldThrow) error("disk unavailable")
                return AppResult.Failure(AppError.Unexpected("disk unavailable"))
            }
        }
        assertEquals(ListenableWorker.Result.success(), worker(f.context, repository) { fail("credited failed purge") }.doWork())
        shouldThrow = true
        assertEquals(ListenableWorker.Result.success(), worker(f.context, repository) { fail("credited throwing purge") }.doWork())
    }

    @Test fun `worker propagates cancellation`() = runTest {
        val f = TrashFixture(temp.root, StandardTestDispatcher(testScheduler))
        val repository = object : TrashRepository by f.repository {
            override suspend fun reconcile(): AppResult<Int> = throw CancellationException("stopped")
        }
        try {
            worker(f.context, repository) {}.doWork()
            fail("Cancellation was swallowed")
        } catch (_: CancellationException) { }
    }

    private fun worker(context: Context, repository: TrashRepository, record: (Long) -> Unit): TrashPurgeWorker {
        val ledger = object : CleanupLedger {
            override suspend fun record(freedBytes: Long): AppResult<Unit> {
                record(freedBytes)
                return AppResult.Success(Unit)
            }
            override fun observeLifetimeFreedBytes() = flowOf(0L)
        }
        val useCase = PurgeExpiredTrashUseCase(repository, ledger)
        return TestListenableWorkerBuilder<TrashPurgeWorker>(context).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                TrashPurgeWorker(appContext, workerParameters, useCase, AppLogger.NoOp)
        }).build()
    }
}
