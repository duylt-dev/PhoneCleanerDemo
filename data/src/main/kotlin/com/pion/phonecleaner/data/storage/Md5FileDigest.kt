package com.pion.phonecleaner.data.storage

import android.content.Context
import android.net.Uri
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.FileDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/**
 * Content identity for `DuplicateFinder` — `DuplicateGroup(md5, files, newestId)` (§4.5). Replaces
 * `od.s0`.
 *
 * **MD5 is an identity here, never a security claim.** Two files with one digest are the same bytes
 * for the purpose of offering to delete one of them; nothing in this app treats a digest as proof of
 * anything. The competitor's own APK hash cache is the same use (`LargeAppDatabase.db`, chapter 15).
 *
 * `ensureActive()` runs once per buffer, so cancelling a duplicate scan stops mid-file instead of
 * finishing a 4 GB read nobody is waiting for.
 *
 * `maxBytes` is the head-digest window of `docs/screens/14-file-tools-and-app-manager.md` §2.2. The
 * read stops at the limit, so a 4 GiB video costs one 64 KiB read in the pass that only has to prove
 * two same-size files differ.
 */
internal class Md5FileDigest(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : FileDigest {

    override suspend fun digest(file: ScannedFile, maxBytes: Long): AppResult<String> =
        withContext(dispatchers.io) {
            try {
                open(file)?.use { hex(it, maxBytes).asSuccess() }
                    ?: AppError.NotFound(file.path).asFailure()
            } catch (e: IOException) {
                AppError.Storage(path = file.path, cause = e.message).asFailure()
            } catch (e: SecurityException) {
                AppError.PermissionDenied(e.message ?: file.path).asFailure()
            }
        }

    /**
     * A row that came out of MediaStore or a SAF tree has a `content://` URI and, in the default
     * branch, **no readable path** — `DATA` is deprecated and unreadable without all-files access on
     * API 29+. Opening by URI is therefore the only way the duplicate finder works in the branch the
     * app actually ships (§8.1). That is why this class takes a `Context`: `docs/system-architecture`
     * §5.6 writes `Md5FileDigest(get())` with one argument, but a digest that can only read paths
     * silently returns "no duplicates" for every media file on a modern device.
     */
    private fun open(file: ScannedFile): InputStream? = when (val origin = file.origin) {
        FileOrigin.PlainFile -> File(file.path).takeIf(File::isFile)?.inputStream()
        is FileOrigin.MediaStoreEntry ->
            context.contentResolver.openInputStream(Uri.parse(origin.contentUri))
    }

    /**
     * Reads at most [maxBytes]. The last buffer is clamped rather than truncated after the fact, so
     * two files that differ only past the limit still produce the same head digest — which is the
     * point of the head pass, and why its result may never be treated as a full-content identity.
     */
    private suspend fun hex(stream: InputStream, maxBytes: Long): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val buffer = ByteArray(BUFFER_BYTES)
        var remaining = maxBytes
        while (remaining > 0) {
            currentCoroutineContext().ensureActive()
            val want = minOf(remaining, BUFFER_BYTES.toLong()).toInt()
            val read = stream.read(buffer, 0, want)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            remaining -= read
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val ALGORITHM = "MD5"
        const val BUFFER_BYTES = 64 * 1024
    }
}
