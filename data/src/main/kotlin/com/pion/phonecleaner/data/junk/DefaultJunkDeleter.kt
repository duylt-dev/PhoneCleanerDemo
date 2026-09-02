package com.pion.phonecleaner.data.junk

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.junk.CleanOutcome
import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.JunkDeleter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Deletes junk paths, one emission per path, choosing a strategy per path
 * (`docs/screens/12-junk-cleaning.md` §5.4).
 *
 * | # | Strategy | Permission | State here |
 * |---|---|---|---|
 * | 1 | our own `cacheDir` / `externalCacheDir` | none | covered by the filesystem branch — those roots are already among `StorageRootProvider.readableRoots()`, and reaching them needs no grant |
 * | 2 | `StorageManager.ACTION_MANAGE_STORAGE` / `allocateBytes` | none | **not implemented — UNKNOWN** |
 * | 3 | `MediaStore.createDeleteRequest()` | media read | delegated to [FileDeleter] |
 * | 4 | SAF `ACTION_OPEN_DOCUMENT_TREE` | user-granted, per folder | delegated to [FileDeleter] |
 *
 * **UNKNOWN — strategy 2.** `StorageManager.allocateBytes` asks the system to free N bytes of *other*
 * apps' caches; it names no path and tells nobody which paths it freed. §5.4 lists it in the
 * preference order, but no source defines how its outcome maps back to the per-path
 * `Deleted`/`Failed` emission `CleanProgress` is built on. Looked for, and not found: a mapping in
 * §5.4, in §5.2's loop and in `docs/system-architecture.md` §8. Inventing an accounting rule would
 * silently credit bytes to paths, so it is left out and named here instead.
 *
 * **UNKNOWN — the consent round trip.** §5.4 says strategy 3 returns
 * `DeleteOutcome.PendingConsent(PendingIntentToken)` and that "the **Route** launches the
 * `IntentSender`". §5.1's `JunkCleanEffect` declares only `NavigateToResult`, `NavigateBack`,
 * `RequestStoragePermission` and `ShowMessage`: no effect can carry the token, and inventing one
 * would be a fabricated contract. The conservative behaviour is taken — a `PendingConsent` is
 * reported as `Failed(path, PermissionDenied)`, so the user is told those paths were **not** removed
 * rather than told they were. The case is currently unreachable in this cluster, because a junk path
 * comes from a filesystem walk or a granted SAF tree and never from a `MediaStore` query.
 *
 * `MANAGE_EXTERNAL_STORAGE` is never assumed grantable (`docs/system-architecture.md` §8.1): nothing
 * here checks for it, and every strategy that needs no grant runs first. `requestLegacyExternalStorage`
 * is not carried over either (Delta C7).
 */
internal class DefaultJunkDeleter(
    private val context: Context,
    private val fileDeleter: FileDeleter,
    private val dispatchers: DispatcherProvider,
) : JunkDeleter {

    override fun delete(paths: Set<String>): Flow<CleanProgress> = flow {
        var freed = 0L
        var deleted = 0
        val failed = ArrayList<String>()
        for (path in paths) {
            currentCoroutineContext().ensureActive()
            when (val result = deleteOne(path)) {
                is PathResult.Removed -> {
                    freed += result.freedBytes
                    deleted++
                    emit(CleanProgress.Deleted(path, result.freedBytes))
                }

                is PathResult.Failed -> {
                    failed += path
                    emit(CleanProgress.Failed(path, result.error))
                }
            }
        }
        emit(CleanProgress.Finished(CleanOutcome(freed, deleted, failed.toImmutableList())))
    }.flowOn(dispatchers.io)

    private suspend fun deleteOne(path: String): PathResult = try {
        if (path.startsWith(DOCUMENT_URI_PREFIX)) deleteDocument(path) else deleteFileTree(path)
    } catch (security: SecurityException) {
        PathResult.Failed(AppError.PermissionDenied(security.message))
    }

    private fun deleteFileTree(path: String): PathResult {
        val tally = deleteJunkTree(File(path), JunkWalkBounds.RULE_DIRECTORY_MAX_DEPTH)
        return if (tally.removed) {
            PathResult.Removed(tally.freedBytes)
        } else {
            PathResult.Failed(AppError.Storage(path = path))
        }
    }

    /**
     * Strategies 3 and 4, through the one deleter in the app.
     *
     * The size is read **before** the delete and carried on the `ScannedFile`, because
     * `DeleteOutcome.Deleted.freedBytes` is the sum of the sizes it was given. The competitor instead
     * re-measures with a full recursive walk immediately before each delete (`c0()`), which is both
     * slower and, on a document URI, impossible.
     */
    private suspend fun deleteDocument(path: String): PathResult {
        val uri = Uri.parse(path)
        val sizeBytes = runCatching { DocumentFile.fromSingleUri(context, uri)?.length() }
            .getOrNull() ?: 0L
        val file = ScannedFile(
            id = path,
            path = path,
            name = path.substringAfterLast('/'),
            sizeBytes = sizeBytes,
            kind = FileKind.Other,
            origin = FileOrigin.MediaStoreEntry(path),
        )
        return when (val result = fileDeleter.delete(listOf(file))) {
            is AppResult.Failure -> PathResult.Failed(result.error)
            is AppResult.Success -> when (val outcome = result.value) {
                is DeleteOutcome.Deleted ->
                    if (outcome.failedPaths.isEmpty()) PathResult.Removed(outcome.freedBytes)
                    else PathResult.Failed(AppError.Storage(path = path))

                is DeleteOutcome.PendingConsent -> PathResult.Failed(AppError.PermissionDenied(path))
                DeleteOutcome.NothingResolved -> PathResult.Failed(AppError.NotFound(path))
            }
        }
    }

    private sealed interface PathResult {
        data class Removed(val freedBytes: Long) : PathResult
        data class Failed(val error: AppError) : PathResult
    }

    private companion object {
        const val DOCUMENT_URI_PREFIX = "content://"
    }
}
