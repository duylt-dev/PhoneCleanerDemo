package com.pion.phonecleaner.domain.repository

/**
 * Which videos this app has already re-encoded (`phase-03-domain-video-compression.md` step 8,
 * key insight 5).
 *
 * Ids, never rows — same rule as `CompressedPhotoLedger`, and the **same purpose**: the row stays
 * in the list instead of vanishing when a successful transcode drops it below the size floor. Fed
 * at the source: the use case that actually wrote the new bytes calls [record], not the result
 * screen. Records the *output* row's id, because after the delete step the source row is gone —
 * that is the only row that survives.
 *
 * **What differs from the photo side is downstream, and it is deliberate.** The candidate row
 * carries `alreadyCompressed`, the picker draws a label on it, and **select-all skips it** — because
 * re-encoding a video a second time is real generation loss even when the output is smaller, whereas
 * the photo engine simply refuses to write a non-smaller file. The user can still select such a row
 * by hand; only select-all passes it over. A reader who assumes total symmetry with the photo side
 * drops that skip; a reader who assumes total divergence hides the row entirely — which is the
 * silent disappearance `CompressedPhotoLedger`'s own KDoc exists to prevent.
 */
interface CompressedVideoLedger {

    /** Every id recorded so far. An empty set is the normal first-run answer, never an error. */
    suspend fun compressedIds(): Set<String>

    /**
     * Records one video as re-encoded by this app. Recording the same id twice is not an error and
     * does not store it twice.
     */
    suspend fun record(id: String)
}
