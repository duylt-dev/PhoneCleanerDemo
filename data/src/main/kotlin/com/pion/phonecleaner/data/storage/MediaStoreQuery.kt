package com.pion.phonecleaner.data.storage

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile

/**
 * **The one `ContentResolver` query builder in `:data/storage`** — the mitigation
 * `docs/system-architecture.md` §10.3 U7 states but does not design.
 *
 * `ScannedFile` and `Photo` are deliberately different types (§4.5): a similar-photo grid needs
 * `perceptualHash` and `takenAt`, a big-file row does not, and putting both on one type puts two
 * nullable fields on every row of a 5 000-item list. **The risk that buys is two query builders
 * drifting** — the same class of defect as the corpus's three disagreeing byte formatters. So the
 * photo cluster's `MediaStorePhotoRepository` projects its own model out of [query] here rather than
 * opening its own cursor.
 *
 * The competitor's three query sites are `nd.c` + `nd.d` + `nd.h`, and it re-resolves a row **by
 * `_display_name`** at delete time although the scan already selected `_id` (`java/nd/g.java:75`).
 * [toScannedFile] therefore resolves the `content://` URI **at scan time** and puts it on
 * [FileOrigin.MediaStoreEntry], so a delete can never hit the wrong row.
 */
internal object MediaStoreQuery {

    val PROJECTION: Array<String> = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        PATH_COLUMN,
    )

    fun imagesUri(): Uri = MediaStore.Images.Media.getContentUri(volume())

    fun videosUri(): Uri = MediaStore.Video.Media.getContentUri(volume())

    fun audioUri(): Uri = MediaStore.Audio.Media.getContentUri(volume())

    /**
     * Runs one query and hands each row to [read]. The cursor never escapes: a `Cursor` that leaves
     * the function that opened it is a `Cursor` nobody closes.
     *
     * Returns `null` when the provider returns no cursor at all — which is not "no rows". A caller
     * that treats the two the same repeats `cd.d.e`'s defect: swallowing a `SecurityException` into
     * an empty map, so a permission failure renders as "nothing found" (§7.6).
     */
    inline fun <T> query(
        context: Context,
        collection: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sortOrder: String? = null,
        read: (Cursor) -> T,
    ): T? = context.contentResolver.query(
        collection,
        PROJECTION,
        selection,
        selectionArgs,
        sortOrder,
    )?.use(read)

    /**
     * `VOLUME_EXTERNAL` is API 29+. Below it, `EXTERNAL_CONTENT_URI` is the only external volume, and
     * `getContentUri("external")` is exactly that URI.
     */
    private fun volume(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.VOLUME_EXTERNAL
        } else {
            LEGACY_EXTERNAL_VOLUME
        }

    private const val LEGACY_EXTERNAL_VOLUME = "external"
}

/**
 * `RELATIVE_PATH` is API 29+ and `DATA` is deprecated but still populated and still the only column
 * carrying a path below 29. Resolved once, so no call site has to remember which.
 *
 * Neither is a *deletable* path — that is what [FileOrigin.MediaStoreEntry] is for. It is carried so
 * a row can be shown with a location and grouped by folder.
 */
private val PATH_COLUMN: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Files.FileColumns.RELATIVE_PATH
    } else {
        @Suppress("DEPRECATION")
        MediaStore.Files.FileColumns.DATA
    }

/**
 * Projects the row the cursor is on. The URI is built from `_id`, never from a display name.
 *
 * Top-level rather than a member of [MediaStoreQuery] so that the photo cluster can import it
 * directly — a member extension of an object cannot be reached without bringing the object's scope
 * along, and U7's whole point is that this projection is easy to reuse and hard to re-write.
 */
internal fun Cursor.toScannedFile(collection: Uri): ScannedFile {
    val id = getLong(getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
    val name = getStringOrEmpty(MediaStore.Files.FileColumns.DISPLAY_NAME)
    val mime = getStringOrNull(MediaStore.Files.FileColumns.MIME_TYPE)
    val uri = ContentUris.withAppendedId(collection, id)
    return ScannedFile(
        id = uri.toString(),
        path = getStringOrEmpty(PATH_COLUMN),
        name = name,
        sizeBytes = getLongOrZero(MediaStore.Files.FileColumns.SIZE),
        kind = FileTypeResolver.of(name, mime),
        origin = FileOrigin.MediaStoreEntry(uri.toString()),
        mimeType = mime,
        // MediaStore keeps DATE_MODIFIED in SECONDS; the model is in milliseconds.
        lastModifiedAtMillis = getLongOrZero(MediaStore.Files.FileColumns.DATE_MODIFIED) * 1_000L,
    )
}

internal fun Cursor.getStringOrNull(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 }?.let { if (isNull(it)) null else getString(it) }

private fun Cursor.getStringOrEmpty(column: String): String = getStringOrNull(column).orEmpty()

private fun Cursor.getLongOrZero(column: String): Long =
    getColumnIndex(column).takeIf { it >= 0 }?.let { if (isNull(it)) 0L else getLong(it) } ?: 0L
