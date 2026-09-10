package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList

/**
 * Files that share a digest. Folds the competitor's `be.j` / `Meditompar`; the proposed
 * `DuplicateFile` lost to [ScannedFile], which already folds five competitor row types
 * (`docs/system-architecture.md` §4.5).
 *
 * A duplicates list is keyed by **`ScannedFile.id`, not by [md5]** — every member of the group shares
 * the md5, so it is not an identity for a row
 * (`docs/screens/21-shared-models-and-ui.md:2.1`, the `MassutoActivity$c` row).
 */
data class DuplicateGroup(
    /** The digest the members share. Group identity, never row identity. */
    val md5: String,
    val files: ImmutableList<ScannedFile>,
    /**
     * [ScannedFile.id] of the newest member — the copy a "keep one" default keeps. Decided from
     * [ScannedFile.lastModifiedAtMillis] by whoever built the group, not recomputed at render.
     */
    val newestId: String,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }

    /** What deleting every copy but [newestId] would free. */
    val reclaimableBytes: Long get() = files.filter { it.id != newestId }.sumOf { it.sizeBytes }

    /**
     * What the whole group is, for the screen's type filter. Read off the first member rather than
     * stored: identical bytes resolve to one [FileKind] through the single `FileTypeResolver`, so a
     * constructor field would be a second place for the same answer to live.
     *
     * A computed `val` on an otherwise immutable data class does not cost the group its Compose
     * skippability — it is not state, and nothing writes it (`LLM.md` §8).
     */
    val kind: FileKind get() = files.firstOrNull()?.kind ?: FileKind.Other
}
