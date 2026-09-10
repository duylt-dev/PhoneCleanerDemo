package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList

/**
 * What the duplicate pipeline reports while it runs
 * (`docs/screens/14-file-tools-and-app-manager.md` §2.2).
 *
 * [Collecting] exists because the corpus is no longer three `MediaStore` queries that answer at
 * once: with all-files access the pipeline walks every mounted volume, and a walk of a full device
 * takes long enough that a screen showing *"compared 0 of 0"* the whole time would look hung.
 *
 * [Hashing] carries both numbers because a digest pass is the slow half and *"how many of how many"*
 * is the only honest progress a content hash can offer.
 *
 * [Finished] carries [truncated] rather than pretending: the competitor's hashing budget is a
 * `withTimeoutOrNull(4000)` that **still reports success**, so a device with 30 000 photos is told it
 * has no duplicates. On expiry this publishes what completed and says the answer is partial.
 */
sealed interface DuplicateScanProgress {

    /** Stage 1 — how many candidate rows the walk and the media queries have yielded so far. */
    data class Collecting(val found: Int) : DuplicateScanProgress

    data class Hashing(val hashed: Int, val candidates: Int) : DuplicateScanProgress

    data class Finished(
        val groups: ImmutableList<DuplicateGroup>,
        val truncated: Boolean = false,
    ) : DuplicateScanProgress
}
