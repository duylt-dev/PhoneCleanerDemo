package com.pion.phonecleaner.data.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.FileDeleter
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * The third primitive of `docs/system-architecture.md` §4.5, and the one that makes the storage
 * branch cheap to switch.
 *
 * The competitor's entire delete strategy is `deleteRecursively` behind `MANAGE_EXTERNAL_STORAGE`,
 * wrapped in `catch (Exception) { printStackTrace() }` returning `0L` (`MenaremovActivity.java:242`,
 * `od/z.java:817-823`) — a total failure is then reported to the user *and to the lifetime ledger* as
 * a total success (`docs/screens/12-junk-cleaning.md:737`). Here a delete reports what actually
 * failed: [DeleteOutcome.Deleted] carries `failedPaths` alongside `freedBytes`.
 *
 * ### The consent round trip
 *
 * On API 30+ a batch of MediaStore rows can only be deleted through
 * `MediaStore.createDeleteRequest`, whose `IntentSender` **only an Activity can launch**. That is why
 * [DeleteOutcome.PendingConsent] exists in *both* branches (§8.4): every delete screen already
 * handles the round trip, so the all-files branch — where the case simply never fires — needs no
 * screen change.
 *
 * The protocol is two calls, and it is idempotent on purpose:
 *
 * 1. `delete(files)` sees rows that still exist and need consent, deletes **nothing**, and returns
 *    `PendingConsent(token, ids)`. Deleting the plain files first would strand their bytes in a
 *    result nobody returns.
 * 2. The Route launches the `IntentSender`, the user accepts, and calls `delete(files)` again. The
 *    media rows are gone, so nothing needs consent, and this pass counts them as freed and deletes
 *    the plain files.
 *
 * A user who refuses simply gets `Deleted` with those ids in `failedPaths` on the second call.
 */
internal class DefaultFileDeleter(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : FileDeleter {

    override suspend fun delete(files: List<ScannedFile>): AppResult<DeleteOutcome> =
        withContext(dispatchers.io) {
            if (files.isEmpty()) return@withContext DeleteOutcome.NothingResolved.asSuccess()
            try {
                consentRequest(files)?.let { return@withContext it.asSuccess() }
                deleteDirectly(files).asSuccess()
            } catch (e: SecurityException) {
                AppError.PermissionDenied(e.message).asFailure()
            }
        }

    /**
     * The rows that still exist and cannot be deleted without the system dialog. Null when there are
     * none — which is both "nothing came from MediaStore" and "the user already consented".
     */
    private fun consentRequest(files: List<ScannedFile>): DeleteOutcome? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val pending = files.filter { it.isMediaStoreRow() && it.stillExists() }
        if (pending.isEmpty()) return null
        val request = MediaStore.createDeleteRequest(
            context.contentResolver,
            pending.map { Uri.parse(it.contentUri()) },
        )
        return DeleteOutcome.PendingConsent(
            request = PendingIntentToken(request.intentSender),
            ids = pending.map(ScannedFile::id).toImmutableList(),
        )
    }

    private fun deleteDirectly(files: List<ScannedFile>): DeleteOutcome {
        val deleted = ArrayList<String>(files.size)
        val failed = ArrayList<String>()
        var freed = 0L
        for (file in files) {
            if (deleteOne(file)) {
                deleted += file.id
                freed += file.sizeBytes
            } else {
                failed += file.path
            }
        }
        return DeleteOutcome.Deleted(
            ids = deleted.toImmutableList(),
            freedBytes = freed,
            failedPaths = failed.toImmutableList(),
        )
    }

    /** True when the file is gone afterwards — including when it was already gone. */
    private fun deleteOne(file: ScannedFile): Boolean = when (val origin = file.origin) {
        FileOrigin.PlainFile -> File(file.path).let { !it.exists() || it.delete() }
        is FileOrigin.MediaStoreEntry -> deleteContentUri(Uri.parse(origin.contentUri))
    }

    /**
     * A `content://` row. Two providers reach this: MediaStore (below API 30, or after consent) and
     * a SAF document from a tree the user granted, whose grant carries the write permission.
     */
    private fun deleteContentUri(uri: Uri): Boolean = runCatching {
        if (uri.isMediaStore()) {
            context.contentResolver.delete(uri, null, null) > 0 || !uri.stillExists()
        } else {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }
    }.getOrElse { cause ->
        // Not rethrown, and not swallowed either: one unreachable row must not fail the whole batch,
        // and the row leaves through `failedPaths` — the half of the outcome the competitor discards.
        // `FileNotFoundException` means the document is already gone, which is the outcome we wanted.
        cause is FileNotFoundException
    }

    private fun ScannedFile.isMediaStoreRow(): Boolean =
        origin is FileOrigin.MediaStoreEntry && Uri.parse(contentUri()).isMediaStore()

    private fun ScannedFile.contentUri(): String = (origin as FileOrigin.MediaStoreEntry).contentUri

    private fun ScannedFile.stillExists(): Boolean = Uri.parse(contentUri()).stillExists()

    private fun Uri.stillExists(): Boolean = runCatching {
        context.contentResolver.query(this, EXISTS_PROJECTION, null, null, null)
            ?.use { it.count > 0 } ?: false
    }.getOrDefault(false)

    private fun Uri.isMediaStore(): Boolean = authority == MediaStore.AUTHORITY

    private companion object {
        val EXISTS_PROJECTION = arrayOf(MediaStore.Files.FileColumns._ID)
    }
}
