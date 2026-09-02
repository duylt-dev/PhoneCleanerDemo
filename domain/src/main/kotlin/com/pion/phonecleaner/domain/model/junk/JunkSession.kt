package com.pion.phonecleaner.domain.model.junk

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlin.time.Instant

/**
 * One scan result, handed from `junkscan` to `junkreview` to `junkclean`.
 *
 * `docs/system-architecture.md` §5.2 permits an argument-free route backed by a session store
 * **only** when the payload is a scan result of unbounded size — which this is — and then requires
 * every consuming route to carry a "session lost to process death" branch. Both do
 * (`JunkReviewEffect.NavigateToScan`, and `junkclean`'s `NavigateBack`).
 *
 * > **Correction, recorded rather than silently applied** (`docs/screens/12-junk-cleaning.md` §1.4).
 * > `r2-03` §B2 defines this type without [selectedPaths] and then reads `s.selectedPaths` in §B3's
 * > `init`. The field is added here, together with
 * > [com.pion.phonecleaner.domain.repository.JunkSessionStore.select], so the review → clean
 * > hand-off has somewhere to live.
 *
 * It replaces three `volatile` statics (`wc.g`) and an uncapped `ArrayList<String>` crossing a
 * Binder transaction (`MenaremovActivity.java:94-102`), which on a device with thousands of residual
 * folders approaches the 1 MB limit and throws on the start (Delta C12).
 */
data class JunkSession(
    val categories: ImmutableList<JunkCategory>,
    val totalBytes: Long,
    /** Every [JunkItem.path] the user still has ticked. Everything starts selected. */
    val selectedPaths: ImmutableSet<String>,
    val scannedAt: Instant,
)
