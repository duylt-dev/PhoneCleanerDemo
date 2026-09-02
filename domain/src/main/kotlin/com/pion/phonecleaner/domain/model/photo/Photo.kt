package com.pion.phonecleaner.domain.model.photo

import kotlin.time.Instant

/**
 * One image row, as the photo cluster projects it
 * (`docs/screens/13-photo-and-media.md` §0.2).
 *
 * **Deliberately separate from `ScannedFile`** (`LLM.md` §12): a similar-photo grid genuinely needs
 * [perceptualHash] and [takenAt], and a big-file row genuinely does not — merging them puts two
 * nullable fields on every row of a 5 000-item list. The mitigation §12 attaches to that decision is
 * mandatory and is honoured: both projections come out of the **one** `ContentResolver` query builder
 * in `:data/storage`, never two hand-rolled cursors.
 *
 * No `@Immutable`: `:domain` is compiled without the Compose plugin (`LLM.md` §2), so the annotation
 * is not on the classpath. `compose-stability.conf` lists `com.pion.phonecleaner.domain.model.*`
 * instead, which is what that file exists for (§8).
 *
 * [sizeBytes] is a `Long`. The competitor's `ce.a.lengthKb` is a `Float` in **kilobytes** and every
 * consumer converts back with `(long) lengthKb * 1024`, so sub-kilobyte precision is lost on every
 * row and the totals drift (`docs/reverse-engineering/13-photo-and-media.md` §4.1).
 *
 * There is no `isSelected` field: selection is a `Set<PhotoId>` on `State` (`LLM.md` §8). The
 * competitor mutates `Likesat.isSelected` on the shared instances, and that mutation *is* how its
 * preview screen synchronises back to the grid — which works only because both screens hold the same
 * objects.
 */
data class Photo(
    val id: PhotoId,
    /**
     * `content://media/external/images/media/<id>`, resolved at **scan** time — never a `_data` path.
     *
     * It is also the `ScannedFile.id` this row maps to when it goes through the shared `FileDeleter`,
     * which is what lets a reducer prune a deleted row with `photo.contentUri in outcome.ids` and no
     * platform type anywhere near it (see `PhotoRepository.delete`).
     *
     * The competitor re-resolves a row **by `_display_name`** at delete time although its scan query
     * already selected `_id` (`java/nd/g.java:75`), so two files with the same name in different
     * folders resolve to whichever row the cursor yields first.
     */
    val contentUri: String,
    val displayName: String,
    /**
     * The full parent path the row sits under — `DCIM/Camera`, not `Camera`.
     *
     * The album index is keyed on this, because the competitor keys on the **name** alone and
     * `DCIM/Camera` and `Pictures/Camera` therefore merge into one row
     * (`docs/screens/13-photo-and-media.md` §6.5). The visible label is the last segment, computed at
     * render (`PhotoAlbum.label`).
     */
    val folderName: String,
    val sizeBytes: Long,
    val takenAt: Instant,
    /**
     * The DCT perceptual hash, or `null` when the file could not be decoded.
     *
     * Nullable on purpose: `od/b0.h` returns `0L` for an undecodable bitmap, so in the competitor
     * **every unreadable file lands in one "similar" group**, their mutual distance being zero
     * (`docs/screens/13-photo-and-media.md` §1.4). A hash failure is not a similarity.
     */
    val perceptualHash: Long? = null,
)
