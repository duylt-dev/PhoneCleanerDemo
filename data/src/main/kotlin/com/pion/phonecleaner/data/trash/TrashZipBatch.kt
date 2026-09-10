package com.pion.phonecleaner.data.trash

import android.os.Environment
import com.pion.phonecleaner.core.common.log.AppLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal data class ZipSource(
    val trashedPath: String,
    val originalPath: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
)

internal data class ZipCreated(
    val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val fileCount: Int,
    val metadataJson: String,
)

internal class TrashZipBatch(
    private val log: AppLogger,
    private val downloadsDir: () -> File = {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), APP_SUBDIR)
    },
) {
    fun create(batchId: String, files: List<ZipSource>): ZipCreated? {
        if (files.isEmpty()) return null
        val dir = downloadsDir()
        if (!dir.exists() && !dir.mkdirs()) return null
        val target = uniqueTarget(dir, "trash_$batchId.zip")
        val usedNames = mutableSetOf<String>()
        val manifest = JSONArray()
        return runCatching {
            ZipOutputStream(target.outputStream().buffered()).use { zip ->
                files.forEachIndexed { index, source ->
                    val entryName = uniqueEntryName(safeEntryName(source.displayName, index), usedNames)
                    File(source.trashedPath).inputStream().use { input ->
                        zip.putNextEntry(ZipEntry(entryName))
                        input.copyTo(zip)
                        zip.closeEntry()
                    }
                    manifest.put(JSONObject()
                        .put("entry", entryName)
                        .put("originalPath", source.originalPath)
                        .put("mimeType", source.mimeType))
                }
            }
            ZipCreated(
                path = target.path,
                displayName = target.name,
                sizeBytes = target.length(),
                fileCount = files.size,
                metadataJson = manifest.toString(),
            )
        }.onFailure {
            log.e(it) { "Could not create trash ZIP batch $batchId" }
            runCatching { target.delete() }
        }.getOrNull()
    }

    suspend fun restore(row: com.pion.phonecleaner.data.database.entity.TrashEntryEntity, mover: TrashMover): TrashZipRestore {
        val manifest = row.metadataJson ?: return TrashZipRestore(restored = 0, failed = 1, renamed = 0)
        var restored = 0
        var failed = 0
        var renamed = 0
        return runCatching {
            val entries = JSONArray(manifest)
            ZipFile(row.trashedPath).use { zip ->
                for (index in 0 until entries.length()) {
                    val item = entries.getJSONObject(index)
                    val zipEntry = zip.getEntry(item.getString("entry"))
                    if (zipEntry == null) {
                        failed++
                        continue
                    }
                    val originalPath = item.getString("originalPath")
                    val target = availableRestoreTarget(File(originalPath))
                    target.parentFile?.mkdirs()
                    zip.getInputStream(zipEntry).use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    mover.registerRestored(target.path, item.optString("mimeType").ifBlank { null })
                    restored++
                    if (target.path != originalPath) renamed++
                }
            }
            TrashZipRestore(restored, failed, renamed)
        }.onFailure { log.e(it) { "Could not restore trash ZIP ${row.id}" } }
            .getOrDefault(TrashZipRestore(restored, failed + 1, renamed))
    }

    private fun availableRestoreTarget(original: File): File {
        if (!original.exists()) return original
        val parent = original.parentFile ?: return original
        val base = original.name.substringBeforeLast('.', original.name)
        val ext = original.name.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = File(parent, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    private fun uniqueTarget(dir: File, displayName: String): File {
        val base = displayName.substringBeforeLast('.', displayName)
        var candidate = File(dir, displayName)
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n).zip")
            n++
        }
        return candidate
    }

    private fun safeEntryName(name: String, index: Int): String =
        name.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001F]"), "").trim()
            .ifBlank { "file-${index + 1}" }

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

    private companion object {
        const val APP_SUBDIR = "PhoneCleaner"
    }
}

internal data class TrashZipRestore(val restored: Int, val failed: Int, val renamed: Int)
