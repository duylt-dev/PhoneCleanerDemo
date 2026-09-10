package com.pion.phonecleaner.domain.testing

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.trash.TrashDirectoryRequest
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashMoveOutcome
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.domain.model.trash.TrashSummary
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.repository.TrashRepository
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.JunkRepository
import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.core.common.error.AppError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Hand-written fakes for `:domain`'s trash use-case tests (`LLM.md` §9 — no mocking library).
 */
internal class FakeTrashRepository(
    var isAvailable: Boolean = true,
    var movedBytes: Long = 0L,
    var failedPaths: List<String> = emptyList(),
) : TrashRepository {

    val movedIds: MutableList<String> = mutableListOf()
    val purgedIds: MutableList<String> = mutableListOf()
    var reconcileCount: Int = 0

    override suspend fun isAvailable(): Boolean = isAvailable

    override suspend fun trashFiles(
        files: List<ScannedFile>,
        source: FeatureId,
    ): AppResult<TrashMoveOutcome> {
        if (!isAvailable) return AppResult.Failure(com.pion.phonecleaner.core.common.error.AppError.Unexpected("Bin unavailable"))

        val ids = files.map { it.id }
        movedIds.addAll(ids)
        return AppResult.Success(
            TrashMoveOutcome(
                movedIds = ids.toImmutableList(),
                movedBytes = movedBytes,
                failedPaths = failedPaths.toImmutableList(),
            ),
        )
    }

    override suspend fun trashDirectory(request: TrashDirectoryRequest): AppResult<TrashMoveOutcome> {
        if (!isAvailable) return AppResult.Failure(com.pion.phonecleaner.core.common.error.AppError.Unexpected("Bin unavailable"))
        return AppResult.Success(
            TrashMoveOutcome(
                movedIds = listOf(request.path).toImmutableList(),
                movedBytes = movedBytes,
                failedPaths = failedPaths.toImmutableList(),
            ),
        )
    }

    override fun observeEntries(): Flow<ImmutableList<TrashEntry>> = flow {
        emit(emptyList<TrashEntry>().toImmutableList())
    }

    override fun observeSummary(): Flow<TrashSummary> = flow {
        emit(TrashSummary())
    }

    override suspend fun restore(ids: List<String>): AppResult<TrashRestoreOutcome> {
        return AppResult.Success(
            TrashRestoreOutcome(
                restoredIds = ids.toImmutableList(),
                failedIds = emptyList<String>().toImmutableList(),
                renamedCount = 0,
            ),
        )
    }

    override suspend fun restoreZip(ids: List<String>): AppResult<TrashRestoreOutcome> = restore(ids)

    override suspend fun deleteForever(ids: List<String>): AppResult<TrashPurgeOutcome> {
        purgedIds.addAll(ids)
        return AppResult.Success(
            TrashPurgeOutcome(
                purgedIds = ids.toImmutableList(),
                freedBytes = movedBytes,
                failedIds = emptyList<String>().toImmutableList(),
            ),
        )
    }

    override suspend fun deleteAllForever(): AppResult<TrashPurgeOutcome> = deleteForever(emptyList())

    override suspend fun purgeExpired(): AppResult<TrashPurgeOutcome> {
        return AppResult.Success(
            TrashPurgeOutcome(
                purgedIds = emptyList<String>().toImmutableList(),
                freedBytes = movedBytes,
                failedIds = emptyList<String>().toImmutableList(),
            ),
        )
    }

    override suspend fun reconcile(): AppResult<Int> {
        reconcileCount++
        return AppResult.Success(0)
    }
}

internal class FakeFileDeleter : FileDeleter {
    val deleteCalls: MutableList<List<ScannedFile>> = mutableListOf()
    var successOutcome: DeleteOutcome.Deleted? = null

    override suspend fun delete(files: List<ScannedFile>): AppResult<DeleteOutcome> {
        deleteCalls.add(files)
        return if (successOutcome != null) {
            AppResult.Success(successOutcome!!)
        } else {
            AppResult.Success(
                DeleteOutcome.Deleted(
                    ids = files.map { it.id }.toImmutableList(),
                    freedBytes = files.sumOf { it.sizeBytes },
                    failedPaths = emptyList<String>().toImmutableList(),
                    recoverable = false,
                ),
            )
        }
    }
}

internal class FakeLedger : CleanupLedger {
    var recordedBytes: Long = 0L

    override suspend fun record(freedBytes: Long): AppResult<Unit> {
        recordedBytes += freedBytes
        return AppResult.Success(Unit)
    }

    override fun observeLifetimeFreedBytes(): Flow<Long> = flow {
        emit(recordedBytes)
    }
}


internal class FakeJunkRepository : JunkRepository {
    val cleanCalls: MutableList<Set<String>> = mutableListOf()
    var estimateInvalidated: Boolean = false

    override fun clean(paths: Set<String>): Flow<CleanProgress> = flow {
        cleanCalls.add(paths)
        emit(CleanProgress.Deleted(paths.first(), 5000L))
        emit(
            CleanProgress.Finished(
                com.pion.phonecleaner.domain.model.junk.CleanOutcome(
                    freedBytes = 5000L,
                    deletedCount = 1,
                    failedPaths = emptyList<String>().toImmutableList(),
                    recoverable = false,
                ),
            ),
        )
    }

    override suspend fun invalidateEstimate() {
        estimateInvalidated = true
    }

    override fun scan(): Flow<ScanProgress> = flow {
        emit(ScanProgress.Finished(emptyList<com.pion.phonecleaner.domain.model.junk.JunkCategory>().toImmutableList(), 0L))
    }

    override fun cachedJunkBytes(): Flow<Long?> = flow {
        emit(0L)
    }

    override suspend fun refreshEstimate(force: Boolean): AppResult<Long> {
        return AppResult.Success(0L)
    }
}
