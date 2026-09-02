package com.pion.phonecleaner.domain.model.file

/**
 * Where a [ScannedFile] came from, and therefore how it is deleted.
 *
 * **The `content://` URI is resolved at SCAN time and carried here**
 * (`docs/system-architecture.md` §4.5). The competitor re-resolves a MediaStore row by
 * `_display_name` at delete time even though its scan query already selected `_id`
 * (`java/nd/g.java:75`), so two files with the same name in different folders resolve to whichever
 * row the cursor yields first. Two clusters found that independently.
 *
 * `contentUri` is a `String`, not an `android.net.Uri`: `:domain` holds no `android.*` type
 * (`LLM.md` §2). `:data` parses it back with `Uri.parse` at the one place that needs it.
 */
sealed interface FileOrigin {

    /** Deleted with `File.delete()`. */
    data object PlainFile : FileOrigin

    /** Deleted through `MediaStore`, using the URI captured by the scan. */
    data class MediaStoreEntry(val contentUri: String) : FileOrigin
}
