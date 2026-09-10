package com.pion.phonecleaner.feature.trash.testing

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.trash.*
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant

internal fun trashEntry(
    id: String = "entry-1",
    displayName: String = "$id.txt",
    sizeBytes: Long = 1_000L,
    trashedAt: Instant = Instant.fromEpochSeconds(0),
    expiresAt: Instant = Instant.fromEpochSeconds(172800),
): TrashEntry = TrashEntry(
    id = id, originalPath = "/storage/emulated/0/$displayName",
    trashedPath = "/storage/emulated/0/.PhoneCleanerTrash/$id-$displayName",
    displayName = displayName, sizeBytes = sizeBytes, fileCount = 1,
    kind = TrashEntryKind.File, mimeType = "text/plain", source = FeatureId.JunkClean,
    trashedAt = trashedAt, expiresAt = expiresAt,
)

/** Real suspension and thrown exceptions are distinct switches; neither is a precomputed failure. */
internal class FakeTrashRepository(
    initialEntries: ImmutableList<TrashEntry> = persistentListOf(),
) : TrashRepository {
    var isAvailable = true
    var restoreCalls = 0
    var restoreZipCalls = 0
    var deleteCalls = 0
    var deleteAllCalls = 0
    var reconcileCalls = 0
    var restoredIds = emptyList<String>()
    var deletedIds = emptyList<String>()
    var suspendRestore = false
    var suspendReconcile = false
    var restoreCancelled = false
    var reconcileCancelled = false
    var restoreException: Throwable? = null
    var deleteException: Throwable? = null
    var reconcileException: Throwable? = null
    var nextRestoreResult: AppResult<TrashRestoreOutcome> = AppResult.Success(
        TrashRestoreOutcome(persistentListOf(), persistentListOf(), 0),
    )
    var nextDeleteResult: AppResult<TrashPurgeOutcome>? = null
    var nextReconcileResult: AppResult<Int> = AppResult.Success(0)
    private val entriesFlow = MutableStateFlow(initialEntries)
    private val summaryFlow = MutableStateFlow(initialEntries.summary())

    override suspend fun isAvailable(): Boolean = isAvailable
    override suspend fun trashFiles(files: List<ScannedFile>, source: FeatureId): AppResult<TrashMoveOutcome> =
        AppResult.Success(TrashMoveOutcome(persistentListOf(), 0, persistentListOf()))
    override suspend fun trashDirectory(request: TrashDirectoryRequest): AppResult<TrashMoveOutcome> =
        AppResult.Success(TrashMoveOutcome(persistentListOf(), 0, persistentListOf()))

    // This models the repository's presentation cap. The all-delete operation deliberately bypasses it.
    override fun observeEntries(): Flow<ImmutableList<TrashEntry>> =
        entriesFlow.map { it.take(500).toImmutableList() }
    override fun observeSummary(): Flow<TrashSummary> = summaryFlow

    override suspend fun restore(ids: List<String>): AppResult<TrashRestoreOutcome> {
        restoreCalls++
        restoredIds = ids
        restoreException?.let { throw it }
        if (suspendRestore) try { awaitCancellation() } finally { restoreCancelled = true }
        return nextRestoreResult
    }

    override suspend fun restoreZip(ids: List<String>): AppResult<TrashRestoreOutcome> {
        restoreZipCalls++
        restoredIds = ids
        return nextRestoreResult
    }

    override suspend fun deleteForever(ids: List<String>): AppResult<TrashPurgeOutcome> {
        deleteCalls++
        deletedIds = ids
        deleteException?.let { throw it }
        return nextDeleteResult ?: deleteRows(ids)
    }

    override suspend fun deleteAllForever(): AppResult<TrashPurgeOutcome> {
        deleteAllCalls++
        deleteException?.let { throw it }
        return nextDeleteResult ?: deleteRows(entriesFlow.value.map { it.id })
    }

    private fun deleteRows(ids: List<String>): AppResult<TrashPurgeOutcome> {
        val removed = entriesFlow.value.filter { it.id in ids }
        setEntries(entriesFlow.value.filterNot { it.id in ids }.toImmutableList())
        return AppResult.Success(TrashPurgeOutcome(
            removed.map { it.id }.toImmutableList(), removed.sumOf { it.sizeBytes }, persistentListOf(),
        ))
    }

    override suspend fun purgeExpired(): AppResult<TrashPurgeOutcome> =
        AppResult.Success(TrashPurgeOutcome(persistentListOf(), 0, persistentListOf()))

    override suspend fun reconcile(): AppResult<Int> {
        reconcileCalls++
        reconcileException?.let { throw it }
        if (suspendReconcile) try { awaitCancellation() } finally { reconcileCancelled = true }
        return nextReconcileResult
    }

    fun setEntries(entries: ImmutableList<TrashEntry>) {
        entriesFlow.value = entries
        summaryFlow.value = entries.summary()
    }

    private fun List<TrashEntry>.summary() = TrashSummary(size, sumOf { it.sizeBytes })
}

internal class FakeClock(var now: Instant = Instant.fromEpochSeconds(0)) : AppClock {
    override fun now(): Instant = now
}
