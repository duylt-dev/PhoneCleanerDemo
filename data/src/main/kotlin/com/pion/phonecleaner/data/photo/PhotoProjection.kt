package com.pion.phonecleaner.data.photo

import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import com.pion.phonecleaner.data.storage.toScannedFile
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

/**
 * `Photo` projected out of the **one** `ContentResolver` query builder in `:data/storage`.
 *
 * That is the mandatory half of `LLM.md` §12's "`ScannedFile` and `Photo` are separate models": the
 * risk two models buy is two query builders drifting, so this one calls
 * `Cursor.toScannedFile(collection)` and re-shapes its output rather than opening a second cursor
 * with a second projection (`docs/system-architecture.md` §10.3 U7).
 */
internal fun Cursor.toPhoto(collection: Uri): Photo? {
    val file = toScannedFile(collection)
    val origin = file.origin as? FileOrigin.MediaStoreEntry ?: return null
    val rowId = runCatching { ContentUris.parseId(Uri.parse(origin.contentUri)) }.getOrNull() ?: return null
    return Photo(
        id = PhotoId(rowId),
        contentUri = origin.contentUri,
        displayName = file.name,
        folderName = folderOf(file.path, file.name),
        sizeBytes = file.sizeBytes,
        // UNKNOWN — DATE_TAKEN. The shared projection (`:data/storage/MediaStoreQuery.PROJECTION`)
        // selects DATE_MODIFIED and no capture date, and that file is the storage layer's, not this
        // cluster's, to edit. Looked for, and not found: a DATE_TAKEN column in that projection, and
        // any statement of the column in `docs/screens/13-photo-and-media.md` §0.2. DATE_MODIFIED is
        // what the competitor's own scan orders by in spirit (`date_added DESC`) and is never null,
        // so it is used and said, rather than a capture date being implied.
        takenAt = Instant.fromEpochMilliseconds(file.lastModifiedAtMillis),
    )
}

/**
 * The row's parent folder, as a **full path** — the album identity of
 * `docs/screens/13-photo-and-media.md` §6.5, where keying on the last segment alone merges
 * `DCIM/Camera` and `Pictures/Camera` into one album.
 *
 * The shared projection's path column is `RELATIVE_PATH` on API 29+ (`DCIM/Camera/`, no file name)
 * and the deprecated `DATA` below it (an absolute path *including* the file name). Rather than branch
 * on the SDK level — which would be a second copy of a decision `MediaStoreQuery` already made — the
 * file name is stripped when it is there.
 */
private fun folderOf(path: String, displayName: String): String {
    val trimmed = path.trimEnd('/')
    val withoutName = when {
        displayName.isEmpty() -> trimmed
        trimmed == displayName -> ""
        trimmed.endsWith("/$displayName") -> trimmed.removeSuffix("/$displayName")
        else -> trimmed
    }
    return withoutName.trim('/').ifEmpty { UNGROUPED_FOLDER }
}

/**
 * Where a row with no readable path goes. Named, so an album called this is obviously ours rather
 * than a folder that happens to exist; the competitor drops such rows silently.
 */
internal const val UNGROUPED_FOLDER: String = "Internal storage"

/**
 * `Photo` -> `ScannedFile`, for the one shared `FileDeleter`. **No second deleter exists in this
 * module** (`docs/screens/13-photo-and-media.md` §0.3).
 *
 * `id` is [Photo.contentUri] deliberately: `DeleteOutcome` hands those ids straight back, so a
 * reducer prunes a deleted row with `photo.contentUri in outcome.ids` and never parses a URI.
 */
internal fun Photo.toScannedFile(): ScannedFile = ScannedFile(
    id = contentUri,
    path = if (folderName.isEmpty()) displayName else "$folderName/$displayName",
    name = displayName,
    sizeBytes = sizeBytes,
    kind = FileKind.Image,
    origin = FileOrigin.MediaStoreEntry(contentUri),
    mimeType = null,
    lastModifiedAtMillis = takenAt.toEpochMilliseconds(),
)

/**
 * Folds rows into albums. The cover is the **newest** photo, explicitly — the competitor's cover is
 * `itemList.first()`, i.e. whatever the cursor returned first, which is not a decision (§6.5).
 *
 * Sorting is not done here: §6.2 puts "by count descending" in the screen's reducer.
 */
internal fun List<Photo>.foldToAlbums(): ImmutableList<PhotoAlbum> = groupBy { it.folderName }
    .map { (folder, rows) ->
        PhotoAlbum(
            folderName = folder,
            coverUri = rows.maxByOrNull { it.takenAt }?.contentUri,
            count = rows.size,
            totalBytes = rows.sumOf { it.sizeBytes },
        )
    }
    .toImmutableList()

/**
 * The two formats every engine in this cluster can re-encode or re-tag, as a `MediaStore` selection.
 *
 * It is the competitor's own filter for the similar scan and the compression scan
 * (`docs/reverse-engineering/13-photo-and-media.md` §4.1, §4.2) — and the one its EXIF scan forgets,
 * which is why that one opens an `ExifInterface` on every image row on the device (§4.4).
 */
internal val IMAGE_MIME_SELECTION: String =
    "${MediaStore.Files.FileColumns.MIME_TYPE} IN (?,?)"

internal val IMAGE_MIME_ARGS: Array<String> = arrayOf("image/jpeg", "image/png")
