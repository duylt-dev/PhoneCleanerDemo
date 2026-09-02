package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList

/**
 * What the duplicate pipeline reports while it runs
 * (`docs/screens/14-file-tools-and-app-manager.md` §2.2).
 *
 * [Hashing] carries both numbers because a digest pass is the slow half and *"how many of how many"*
 * is the only honest progress a content hash can offer.
 *
 * [Finished] carries [truncated] rather than pretending: the competitor's hashing budget is a
 * `withTimeoutOrNull(4000)` that **still reports success**, so a device with 30 000 photos is told it
 * has no duplicates. On expiry this publishes what completed and says the answer is partial.
 */
sealed interface DuplicateScanProgress {

    data class Hashing(val hashed: Int, val candidates: Int) : DuplicateScanProgress

    data class Finished(
        val groups: ImmutableList<DuplicateGroup>,
        val truncated: Boolean = false,
    ) : DuplicateScanProgress
}
