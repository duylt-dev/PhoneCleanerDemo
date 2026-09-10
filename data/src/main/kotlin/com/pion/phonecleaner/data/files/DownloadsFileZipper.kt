package com.pion.phonecleaner.data.files

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.ZipFileOutcome
import com.pion.phonecleaner.domain.model.file.ZipFileRequest
import com.pion.phonecleaner.domain.repository.FileZipper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class DownloadsFileZipper(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : FileZipper {

    override suspend fun createZip(request: ZipFileRequest): AppResult<ZipFileOutcome> =
        withContext(dispatchers.io) {
            val target = openTarget(request.fileName)
                ?: return@withContext AppResult.Failure(AppError.Storage(request.fileName))
            try {
                target.output.use { out ->
                    ZipOutputStream(out.buffered()).use { zip ->
                        val usedNames = mutableSetOf<String>()
                        request.files.forEachIndexed { index, input ->
                            currentCoroutineContext().ensureActive()
                            val entryName = uniqueEntryName(safeEntryName(input.displayName, index), usedNames)
                            val stream = context.contentResolver.openInputStream(Uri.parse(input.uri))
                                ?: return@withContext failAndDiscard(target, request.fileName)
                            stream.use {
                                zip.putNextEntry(ZipEntry(entryName))
                                val buffer = ByteArray(BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = it.read(buffer)
                                    if (read < 0) break
                                    zip.write(buffer, 0, read)
                                }
                                zip.closeEntry()
                            }
                        }
                    }
                }
                target.finish()
                AppResult.Success(
                    ZipFileOutcome(
                        uri = target.uri,
                        fileName = request.fileName,
                        inputCount = request.files.size,
                        inputBytes = request.files.sumOf { it.sizeBytes.coerceAtLeast(0L) },
                        outputBytes = target.length(),
                    ),
                )
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                target.discard()
                throw cancelled
            } catch (failure: Exception) {
                log.e(failure) { "Could not create ZIP ${request.fileName}" }
                failAndDiscard(target, request.fileName)
            }
        }

    private fun failAndDiscard(target: ZipTarget, fileName: String): AppResult<ZipFileOutcome> {
        target.discard()
        return AppResult.Failure(AppError.Storage(fileName))
    }

    private fun openTarget(fileName: String): ZipTarget? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) openScopedTarget(fileName) else openLegacyTarget(fileName)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun openScopedTarget(fileName: String): ZipTarget? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, ZIP_MIME)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$APP_SUBDIR")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        val output = context.contentResolver.openOutputStream(uri) ?: run {
            context.contentResolver.delete(uri, null, null)
            return null
        }
        return object : ZipTarget {
            override val uri: String = uri.toString()
            override val output: OutputStream = output
            override fun length(): Long = querySize(uri)
            override fun finish() {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                    null,
                    null,
                )
            }
            override fun discard() {
                runCatching { output.close() }
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
        }
    }

    private fun openLegacyTarget(fileName: String): ZipTarget? {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_SUBDIR)
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = uniqueLegacyTarget(dir, fileName)
        return runCatching {
            val output = target.outputStream()
            object : ZipTarget {
                override val uri: String = Uri.fromFile(target).toString()
                override val output: OutputStream = output
                override fun length(): Long = target.length()
                override fun finish() = Unit
                override fun discard() {
                    runCatching { output.close() }
                    runCatching { target.delete() }
                }
            }
        }.onFailure { log.e(it) { "Could not open legacy ZIP target $fileName" } }.getOrNull()
    }

    private fun querySize(uri: Uri): Long {
        val projection = arrayOf(MediaStore.Downloads.SIZE)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        } ?: 0L
    }

    private fun uniqueLegacyTarget(dir: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.', displayName)
        val ext = displayName.substringAfterLast('.', "")
        var candidate = File(dir, displayName)
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        return candidate
    }

    private fun safeEntryName(name: String, index: Int): String {
        val cleaned = name
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001F]"), "")
            .trim()
        return cleaned.ifBlank { "file-${index + 1}" }
    }

    private fun uniqueEntryName(name: String, used: MutableSet<String>): String {
        if (used.add(name)) return name
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext"
            if (used.add(candidate)) return candidate
            n++
        }
    }

    private interface ZipTarget {
        val uri: String
        val output: OutputStream
        fun length(): Long
        fun finish()
        fun discard()
    }

    private companion object {
        const val APP_SUBDIR = "PhoneCleaner"
        const val ZIP_MIME = "application/zip"
        const val BUFFER_SIZE = 64 * 1024
    }
}
