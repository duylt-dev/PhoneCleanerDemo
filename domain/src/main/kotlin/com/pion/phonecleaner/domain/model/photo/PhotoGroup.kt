package com.pion.phonecleaner.domain.model.photo

import kotlinx.collections.immutable.ImmutableList

/**
 * A run of photos the screen draws under one header: a similar-photo bucket (§1), or a month bucket
 * (§3, §5) — `docs/screens/13-photo-and-media.md` §0.2.
 *
 * [key] is the identity and is unique within one scan; [label] is what the header renders.
 * They are **two fields on purpose**: the competitor's header label is the opener's `yyyy-MM-dd`
 * and it has no key at all, so two unrelated groups shot on the same day render the same header text
 * and nothing can tell them apart (`docs/reverse-engineering/13-photo-and-media.md` §4.1 step 5).
 *
 * [label] is a machine-shaped string — `yyyy-MM-dd` for a similar group, `yyyy-MM` for a month bucket
 * — because `:domain` has no `Resources` and must not format user copy (MVI §5). The screen turns it
 * into a localised heading at render.
 */
data class PhotoGroup(
    val key: String,
    val label: String,
    val photos: ImmutableList<Photo>,
) {
    val totalBytes: Long get() = photos.sumOf { it.sizeBytes }

    /**
     * The member a "keep one" default keeps: the newest, which is the first — the scan orders every
     * group by `takenAt` descending, exactly as `date_added DESC` does for the competitor
     * (`docs/reverse-engineering/13-photo-and-media.md` §4.1 step 4).
     */
    val keptId: PhotoId? get() = photos.firstOrNull()?.id
}
