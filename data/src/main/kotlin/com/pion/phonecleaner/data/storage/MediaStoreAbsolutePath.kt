package com.pion.phonecleaner.data.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * [MediaStoreQuery.absolutePathFor] and its fallback, split into their own file to keep
 * `MediaStoreQuery.kt` under the 200-line rule. An extension on the object, not a member, so the call
 * site stays `MediaStoreQuery.absolutePathFor(context, uri)` — it reaches [MediaStoreQuery.query] (a
 * public member) and [getStringOrNull] (an internal top-level, `MediaStoreQuery.kt`'s own convention
 * for the projection helpers) the same way a member function would.
 */

/**
 * The absolute path behind a media row, resolved from `DATA`.
 *
 * `DATA` is deprecated and is NOT selected by [MediaStoreQuery.PROJECTION] on API 29+, where the
 * projection takes `RELATIVE_PATH` instead — a **folder**, which is what `LLM.md` §11 row 10 records
 * as a live defect in `ScanBigFilesUseCase`. Deprecated is not removed: the column is still populated
 * and still readable, and it is the only thing that answers "which file on disk is this row" in one
 * query. Reading it here, once, is what keeps that mistake out of the trash mover.
 *
 * Returns null when the column is null or empty — a row with no backing file cannot be moved, and the
 * caller reports it in `failedPaths` rather than guessing a path.
 */
internal fun MediaStoreQuery.absolutePathFor(context: Context, uri: Uri): String? {
    val fromData = query(context, uri, projection = DATA_PROJECTION) { cursor ->
        if (cursor.moveToFirst()) cursor.getStringOrNull(DATA_COLUMN) else null
    }
    if (!fromData.isNullOrBlank()) return fromData
    return fallbackAbsolutePath(context, uri)
}

/**
 * Rebuilds a path from `VOLUME_NAME` + `RELATIVE_PATH` + `DISPLAY_NAME` when `DATA` came back blank.
 * Only the primary shared volume is resolved this way — `TrashRoots.resolve`'s own rule already
 * refuses to guess a foreign volume's root, and a secondary volume's `VOLUME_NAME` is an opaque
 * per-device UUID with no public API this project depends on to reverse; returning null there is the
 * same "never guess a root" discipline, not an oversight.
 */
private fun fallbackAbsolutePath(context: Context, uri: Uri): String? {
    val row = MediaStoreQuery.query(context, uri, projection = FALLBACK_PROJECTION) { cursor ->
        if (!cursor.moveToFirst()) return@query null
        Triple(
            cursor.getStringOrNull(MediaStore.MediaColumns.VOLUME_NAME),
            cursor.getStringOrNull(MediaStore.MediaColumns.RELATIVE_PATH),
            cursor.getStringOrNull(MediaStore.MediaColumns.DISPLAY_NAME),
        )
    } ?: return null
    val (volumeName, relativePath, displayName) = row
    if (relativePath.isNullOrEmpty() || displayName.isNullOrEmpty()) return null
    val knownPrimaryVolume = volumeName == null ||
        volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY ||
        volumeName == MediaStoreQuery.LEGACY_EXTERNAL_VOLUME
    if (!knownPrimaryVolume) return null
    val primaryRoot = Environment.getExternalStorageDirectory() ?: return null
    return File(File(primaryRoot, relativePath), displayName).absolutePath
}

/** `DATA` alone — [MediaStoreQuery.absolutePathFor]'s primary read needs no other column. */
@Suppress("DEPRECATION")
private val DATA_PROJECTION: Array<String> = arrayOf(MediaStore.Files.FileColumns.DATA)

@Suppress("DEPRECATION")
private val DATA_COLUMN: String = MediaStore.Files.FileColumns.DATA

private val FALLBACK_PROJECTION: Array<String> = arrayOf(
    MediaStore.MediaColumns.VOLUME_NAME,
    MediaStore.MediaColumns.RELATIVE_PATH,
    MediaStore.MediaColumns.DISPLAY_NAME,
)
