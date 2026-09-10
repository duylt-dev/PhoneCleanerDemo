package com.pion.phonecleaner.data.trash

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.data.junk.JunkWalkBounds
import com.pion.phonecleaner.data.storage.MediaStoreQuery
import com.pion.phonecleaner.data.storage.absolutePathFor
import com.pion.phonecleaner.data.storage.walkFilesBounded
import com.pion.phonecleaner.domain.model.file.WalkConfig
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.file.Files
import kotlin.coroutines.resume

/**
 * The physical half: the move, the restore, the permanent delete, and the media-row bookkeeping
 * around a move. Never decides retention or state — `RoomTrashRepository` does that; this class only
 * touches the filesystem and reports what happened.
 */
internal class TrashMover(
    private val context: Context,
    private val roots: TrashRoots,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
    private val rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) },
    private val restoreMove: (File, File) -> Boolean = { from, to -> Files.move(from.toPath(), to.toPath()); true },
    private val remove: (File) -> Boolean = { if (it.isDirectory) it.deleteRecursively() else it.delete() },
    private val restoredIndex: (suspend (String, String?) -> Unit)? = null,
) {

    /**
     * The real absolute filesystem path behind a scanned file.
     *
     * `ScannedFile.path` for a `MediaStore`-origin row is `RELATIVE_PATH` on API 29+ — a **folder**,
     * not a file (`LLM.md` §11 row 10, `MediaStoreQuery.absolutePathFor`'s own KDoc). Renaming that
     * string would rename a directory, or nothing. [contentUri] non-null means the row came from
     * `MediaStore`, so the real path is read off its `DATA` column (or the fallback reconstruction)
     * instead of trusting [scannedPath]. A plain-file origin's [scannedPath] is already absolute
     * (`DefaultStorageScanner.toScannedFile`) and is returned as-is.
     *
     * Null means no real path could be resolved — the caller reports the row as failed rather than
     * attempting a rename against a folder, a `content://` string, or a row with no backing file.
     */
    suspend fun absolutePathOf(scannedPath: String, contentUri: String?): String? = withContext(dispatchers.io) {
        if (contentUri != null) {
            MediaStoreQuery.absolutePathFor(context, Uri.parse(contentUri))
        } else {
            scannedPath.takeIf { it.startsWith("/") }
        }
    }

    /**
     * The destination [moveIn] would use for [sourcePath], creating the trash root if needed but
     * touching neither [sourcePath] nor the row. Null means refused — no absolute path, already
     * inside a trash root, or no writable root on that volume.
     *
     * Resolved once before the PENDING insert; a second resolution could orphan the row.
     * The UUID prefix keeps two identically named files distinct.
     */
    suspend fun resolveDestination(sourcePath: String, entryId: String): File? = withContext(dispatchers.io) {
        // A SAF document URI arrives in ScannedFile.path (SafTreeWalk.kt) — nothing to rename.
        if (!sourcePath.startsWith("/")) return@withContext null
        // Idempotence against a double tap: refuse a source already inside a trash root.
        val canonical = File(sourcePath).canonicalPath
        if (roots.allRoots().any { canonical == it || canonical.startsWith("$it/") }) return@withContext null
        val destRoot = roots.resolve(canonical) ?: return@withContext null
        File(destRoot, "$entryId-${File(sourcePath).name}")
    }

    /**
     * Renames [sourcePath] to [destination] — precomputed by [resolveDestination] so the row committed
     * before this call and the path this call actually uses can never disagree.
     *
     * On any failure to verify a clean move, **nothing is touched further** — no copy, no delete, no
     * "clean up". [MoveResult.Refused] is the whole answer; the caller puts the path in `failedPaths`.
     */
    suspend fun moveIn(sourcePath: String, destination: File): MoveResult = withContext(dispatchers.io) {
        val source = File(sourcePath)
        val renamed = runCatching { rename(source, destination) }
            .onFailure { log.e(it) { "Trash move threw for $sourcePath" } }
            .getOrDefault(false)
        if (renamed && destination.exists() && !source.exists()) {
            MoveResult.Moved(destination.absolutePath)
        } else {
            MoveResult.Refused
        }
    }

    /**
     * Never overwrites: an occupied [originalPath] gets a suffixed name beside it,
     * `name (1).ext` .. `name (2).ext`, up to 99, then fails. The occupant may be a file the user made
     * since trashing this one; overwriting it destroys data the bin exists to protect.
     */
    suspend fun moveOut(trashedPath: String, originalPath: String): RestoreResult = withContext(dispatchers.io) {
        val source = File(trashedPath)
        if (!roots.canInspect(trashedPath)) return@withContext RestoreResult.Failed
        if (!source.exists()) return@withContext RestoreResult.Failed
        val original = File(originalPath)
        val parent = original.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) return@withContext RestoreResult.Failed

        val target = if (!original.exists()) original else freeSuffixedName(original)
        if (target == null) return@withContext RestoreResult.Failed
        // Files.move without REPLACE_EXISTING refuses a target created after our name check.
        val renamed = runCatching { restoreMove(source, target) }
            .onFailure { log.e(it) { "Trash restore threw for $trashedPath" } }
            .getOrDefault(false)
        if (renamed && target.exists() && !source.exists()) {
            RestoreResult.Restored(target.absolutePath, renamed = target != original)
        } else {
            RestoreResult.Failed
        }
    }

    /**
     * Size + child count, bounded exactly like `deleteJunkTree` (`JunkWalkBounds.RULE_DIRECTORY_MAX_DEPTH`).
     * Called once, at move time; the result is stored on the row and never re-walked.
     */
    suspend fun measure(directory: File): Measured = withContext(dispatchers.io) {
        var bytes = 0L
        var count = 0
        val config = WalkConfig(
            roots = persistentListOf(directory.absolutePath),
            maxDepth = JunkWalkBounds.RULE_DIRECTORY_MAX_DEPTH,
        )
        walkFilesBounded(listOf(directory.absolutePath), config) { file ->
            bytes += file.length()
            count++
        }
        Measured(bytes, count)
    }

    /** Bytes actually removed: measured before the delete, credited only if the delete fully succeeded. */
    suspend fun deletePermanently(path: String): PurgeResult = withContext(dispatchers.io) {
        if (!roots.canInspect(path)) return@withContext PurgeResult(0L, false)
        val target = File(path)
        if (!target.exists()) return@withContext PurgeResult(0L, true)
        val bytes = if (target.isDirectory) measure(target).sizeBytes else target.length()
        runCatching { remove(target) }.onFailure { log.e(it) { "Permanent delete threw for $path" } }
        val remains = target.exists()
        val after = if (!remains) 0L else if (target.isDirectory) measure(target).sizeBytes else target.length()
        PurgeResult((bytes - after).coerceAtLeast(0L), !remains)
    }

    /**
     * `ContentResolver.delete` first — no consent dialog, because this path is only taken with
     * `AppPermission.AllFiles` held (E2) — then `MediaScannerConnection.scanFile` on the vacated path,
     * belt and braces. `false` means the delete threw (typically `SecurityException`); the caller
     * records `media_row_cleared = false` and carries on — never a consent request at this point, since
     * the file has already moved.
     */
    suspend fun clearMediaRow(contentUri: String?, vacatedPath: String): Boolean = withContext(dispatchers.io) {
        if (contentUri == null) return@withContext true
        val cleared = runCatching { context.contentResolver.delete(Uri.parse(contentUri), null, null); true }
            .onFailure { log.e(it) { "Could not clear stale media row $contentUri" } }
            .getOrDefault(false)
        runCatching { MediaScannerConnection.scanFile(context, arrayOf(vacatedPath), null, null) }
        cleared
    }

    /** The restore-side mirror of `VideoOutputPublisher.scanAndAwaitUri` — same bounded wait, same call. */
    suspend fun registerRestored(path: String, mimeType: String?) {
        restoredIndex?.let { it(path, mimeType); return }
        withContext(dispatchers.io) {
            withTimeoutOrNull(SCAN_TIMEOUT_MILLIS) {
                suspendCancellableCoroutine<Uri?> { cont ->
                    val mimeTypes = mimeType?.let { arrayOf(it) }
                    MediaScannerConnection.scanFile(context, arrayOf(path), mimeTypes) { _, uri ->
                        if (cont.isActive) cont.resume(uri)
                    }
                }
            }
        }
    }

    private fun freeSuffixedName(original: File): File? {
        val base = original.nameWithoutExtension
        val ext = original.extension
        val parent = original.parentFile ?: return null
        for (n in 1..MAX_SUFFIX_ATTEMPTS) {
            val name = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            val candidate = File(parent, name)
            if (!candidate.exists()) return candidate
        }
        return null
    }

    private companion object {
        const val MAX_SUFFIX_ATTEMPTS = 99

        /** Generous for a single-file index; the point is that it is finite (`VideoOutputPublisher`). */
        const val SCAN_TIMEOUT_MILLIS = 30_000L
    }
}
