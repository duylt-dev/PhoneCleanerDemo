package com.pion.phonecleaner.domain.model.photo

/**
 * One folder of the album index (`docs/screens/13-photo-and-media.md` §0.2, §6).
 *
 * [folderName] is the **full parent path**, which is the identity: the competitor groups by
 * `nd/d.a()` on the folder *name*, so `DCIM/Camera` and `Pictures/Camera` merge into one row (§6.5).
 * [label] is the last segment and is derived, never stored — two fields that can disagree will.
 */
data class PhotoAlbum(
    val folderName: String,
    /**
     * The `content://` URI of the album's **newest** photo, or null for an album whose rows all
     * vanished between the query and the fold.
     *
     * "Newest", explicitly. The competitor's cover is `itemList.first()`, i.e. whatever the cursor
     * happened to return first — which is not a decision (§6.5).
     */
    val coverUri: String?,
    val count: Int,
    val totalBytes: Long,
) {
    val label: String get() = folderName.trimEnd('/').substringAfterLast('/').ifEmpty { folderName }
}
