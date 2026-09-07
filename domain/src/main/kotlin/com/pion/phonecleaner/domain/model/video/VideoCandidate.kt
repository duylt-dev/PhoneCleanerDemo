package com.pion.phonecleaner.domain.model.video

import com.pion.phonecleaner.domain.model.file.ScannedFile

/**
 * One video the compressor may offer (`phase-03-domain-video-compression.md` step 2).
 *
 * **Composition, not a widened [ScannedFile].** `LLM.md` §12 is explicit that `ScannedFile` and
 * `Photo` are kept separate because merging them "puts two nullable fields on every row of a
 * 5 000-item list" — the same reasoning applies here: [durationMs]/[width]/[height] are the video
 * cluster's own need, and the five other file tools that already share [ScannedFile] must not carry
 * them. The delete path still hands `FileDeleter` a plain [ScannedFile], unchanged, off [file].
 *
 * **[alreadyCompressed] is a field on the model, and that is legal.** It looks like a direct
 * violation of "selection is a `Set<Id>` on `State`" and it is not: it is persisted repository
 * truth (whether [com.pion.phonecleaner.domain.repository.CompressedVideoLedger] has this id),
 * never a tap. `LLM.md` §12 blesses exactly this shape for `LockableApp.isLocked`, with the same
 * reason — holding it on `State` would need a write-back path a selection never needs.
 */
data class VideoCandidate(
    val file: ScannedFile,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val alreadyCompressed: Boolean = false,
) {
    /** Delegates so the picker's `SelectableFiles` key and `FileDeleter`'s identity stay one string. */
    val id: String get() = file.id
    val sizeBytes: Long get() = file.sizeBytes

    /** `false` when MediaStore had no dimensions for the row. Never a guess, never a 0 treated as real. */
    val hasDimensions: Boolean get() = width > 0 && height > 0

    /** `false` when MediaStore had no usable duration. Same refusal as [hasDimensions]. */
    val hasDuration: Boolean get() = durationMs > 0L
}
