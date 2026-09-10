package com.pion.phonecleaner.data.trash

import android.content.Context
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.data.database.TrashEntryDao
import com.pion.phonecleaner.data.database.entity.TrashEntryEntity
import com.pion.phonecleaner.data.database.entity.TrashEntryTypeRow
import com.pion.phonecleaner.data.database.entity.TrashRowState
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.trash.TrashDirectoryRequest
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashMoveOutcome
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.domain.model.trash.TrashSummary
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/**
 * The port implementation. Shape from `RoomNotificationCleanerRepository`: `internal class`, `Flow`
 * reads with `.flowOn(dispatchers.io)`, suspend writes through a private `io(what) { }` returning
 * [AppResult]. `TrashCommit` and `TrashReconciler` are internal collaborators this class constructs,
 * not Koin bindings — the same shape `Media3VideoCompressor` builds `VideoTranscodeSession`.
 *
 * Every per-item failure (a refused move, a missing row, an occupied restore target) is reported on
 * the outcome's own `failed*` list; this class returns [AppResult.Failure] only for a genuinely
 * unexpected exception — never for the ordinary "this one file could not move" case (T7's per-file
 * refusal is a data point, not an error the caller must branch on `is Failure` to see).
 */
internal class RoomTrashRepository(
    private val dao: TrashEntryDao,
    private val roots: TrashRoots,
    private val mover: TrashMover,
    private val clock: AppClock,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
    private val context: Context,
    schedulePurge: (() -> Unit)? = null,
    zipBatch: TrashZipBatch = TrashZipBatch(log),
) : TrashRepository {

    // One repository singleton owns every mutation: a worker must never reconcile a live move.
    private val mutationLock = Mutex()
    private val commit = TrashCommit(dao, mover, clock, log, context, schedulePurge)
    private val reconciler = TrashReconciler(dao, log, roots)
    private val removal = TrashRemoval(dao, mover)
    private val zipBatch = zipBatch

    override suspend fun isAvailable(): Boolean = withContext(dispatchers.io) { roots.isAvailable() }

    override suspend fun trashFiles(files: List<ScannedFile>, source: FeatureId): AppResult<TrashMoveOutcome> =
        io("trashFiles") {
            val movedIds = mutableListOf<String>()
            val failedPaths = mutableListOf<String>()
            val zipSources = mutableListOf<ZipSource>()
            var movedBytes = 0L
            val batchId = UUID.randomUUID().toString()
            for (file in files) {
                currentCoroutineContext().ensureActive()
                val contentUri = (file.origin as? FileOrigin.MediaStoreEntry)?.contentUri
                // ScannedFile.path is RELATIVE_PATH — a folder, not a file — for a MediaStore row on
                // API 29+ (LLM.md §11 row 10); resolve the real absolute path off the row itself
                // before anything else touches it.
                val absolutePath = mover.absolutePathOf(file.path, contentUri)
                val trashedPath = absolutePath?.let { commit.commit(file.toCommitRequest(source, it, contentUri, batchId)) }
                if (trashedPath != null) {
                    movedIds += file.id
                    movedBytes += file.sizeBytes
                    if (file.isTrashZipEligible()) {
                        zipSources += ZipSource(trashedPath, absolutePath, file.name, file.sizeBytes, file.mimeType)
                    }
                } else {
                    failedPaths += file.path
                }
            }
            val zipFailed = movedIds.isNotEmpty() && zipSources.isNotEmpty() && createZipEntry(batchId, source, zipSources) == null
            TrashMoveOutcome(movedIds.toImmutableList(), movedBytes, failedPaths.toImmutableList(), zipFailed = zipFailed)
        }

    override suspend fun trashDirectory(request: TrashDirectoryRequest): AppResult<TrashMoveOutcome> =
        io("trashDirectory") {
            val directory = File(request.path)
            val measured = mover.measure(directory)
            val commitRequest = CommitRequest(
                id = UUID.randomUUID().toString(),
                originalPath = request.path,
                displayName = directory.name,
                sizeBytes = measured.sizeBytes,
                fileCount = measured.fileCount,
                isDirectory = true,
                mimeType = null,
                source = request.source,
                originContentUri = null,
            )
            if (commit.commit(commitRequest) != null) {
                TrashMoveOutcome(
                    movedIds = persistentListOf(request.path),
                    movedBytes = measured.sizeBytes,
                    failedPaths = persistentListOf(),
                    fileCount = measured.fileCount,
                )
            } else {
                TrashMoveOutcome(persistentListOf(), 0L, persistentListOf(request.path))
            }
        }

    override fun observeEntries(): Flow<ImmutableList<TrashEntry>> =
        dao.observeTrashed().map { rows -> rows.map { it.toModel() }.toImmutableList() }.flowOn(dispatchers.io)

    override fun observeSummary(): Flow<TrashSummary> =
        dao.observeSummary().map { it.toModel() }.flowOn(dispatchers.io)

    override suspend fun restore(ids: List<String>): AppResult<TrashRestoreOutcome> =
        io("restore") { removal.restore(ids.distinct()) }

    override suspend fun deleteForever(ids: List<String>): AppResult<TrashPurgeOutcome> = io("deleteForever") {
        val found = dao.byIds(ids).associateBy { it.id }
        val missing = ids.filterNot { it in found }
        val outcome = removal.purge(found.values.toList())
        if (missing.isEmpty()) outcome else outcome.copy(failedIds = (outcome.failedIds + missing).toImmutableList())
    }

    override suspend fun purgeExpired(): AppResult<TrashPurgeOutcome> = io("purgeExpired") {
        removal.purge(dao.expired(clock.now().toEpochMilliseconds()))
    }

    override suspend fun deleteAllForever(): AppResult<TrashPurgeOutcome> =
        io("deleteAllForever") { removal.purge(dao.allTrashed()) }

    override suspend fun restoreZip(ids: List<String>): AppResult<TrashRestoreOutcome> = io("restoreZip") {
        val rows = dao.byIds(ids.distinct()).filter {
            it.state == TrashRowState.TRASHED.name && it.entryType == TrashEntryTypeRow.ZIP.name
        }
        val restored = mutableListOf<String>()
        val failed = ids.distinct().filterNot { id -> rows.any { it.id == id } }.toMutableList()
        var renamed = 0
        rows.forEach { row ->
            val result = zipBatch.restore(row, mover)
            if (result.failed == 0 && result.restored > 0) {
                removal.purge(listOf(row))
                restored += row.id
            } else {
                failed += row.id
            }
            renamed += result.renamed
        }
        TrashRestoreOutcome(restored.toImmutableList(), failed.toImmutableList(), renamed)
    }

    override suspend fun reconcile(): AppResult<Int> = io("reconcile") { reconciler.reconcile() }

    /** [resolvedPath] is the real filesystem path — see [TrashMover.absolutePathOf] — never [ScannedFile.path] directly. */
    private fun ScannedFile.toCommitRequest(
        source: FeatureId,
        resolvedPath: String,
        contentUri: String?,
        batchId: String,
    ) = CommitRequest(
        id = UUID.randomUUID().toString(),
        originalPath = resolvedPath,
        displayName = name,
        sizeBytes = sizeBytes,
        fileCount = 1,
        isDirectory = false,
        mimeType = mimeType,
        source = source,
        originContentUri = contentUri,
        batchId = batchId,
    )

    private suspend fun createZipEntry(batchId: String, source: FeatureId, files: List<ZipSource>): String? {
        val created = zipBatch.create(batchId, files) ?: return null
        val row = newPendingTrashEntry(
            id = UUID.randomUUID().toString(),
            originalPath = created.path,
            trashedPath = created.path,
            displayName = created.displayName,
            sizeBytes = created.sizeBytes,
            fileCount = created.fileCount,
            isDirectory = false,
            mimeType = ZIP_MIME,
            source = source,
            originContentUri = null,
            entryType = TrashEntryTypeRow.ZIP,
            batchId = batchId,
            metadataJson = created.metadataJson,
            trashedAt = clock.now(),
        )
        dao.insert(row)
        dao.setState(row.id, TrashRowState.TRASHED.name)
        return created.path
    }

    private fun ScannedFile.isTrashZipEligible(): Boolean =
        kind == FileKind.Image || kind == FileKind.Video || kind == FileKind.Audio ||
            mimeType?.let { it.startsWith("image/") || it.startsWith("video/") || it.startsWith("audio/") } == true

    private companion object {
        const val ZIP_MIME = "application/zip"
    }

    private suspend fun <T> io(what: String, block: suspend () -> T): AppResult<T> =
        withContext(dispatchers.io) { mutationLock.withLock {
            runCatching { block() }.fold(
                onSuccess = { AppResult.Success(it) },
                onFailure = {
                    if (it is CancellationException) throw it
                    log.e(it) { "Trash $what failed" }
                    AppResult.Failure(AppError.Unexpected(it.message))
                },
            )
        } }
}
