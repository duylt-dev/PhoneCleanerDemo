package com.pion.phonecleaner.domain.model.photo

import com.pion.phonecleaner.core.common.error.AppError
import kotlinx.collections.immutable.ImmutableList

/**
 * What the blurry-photo scan reports while it runs.
 *
 * The same three arms as `SimilarScanProgress`, and separate from it on purpose: the two scans
 * measure different things, and folding them into one type would make the arm names lie about which
 * engine is running. Real counts, emitted as the work happens — the competitor pads its scans to a
 * fixed 4 000 ms floor and then reveals the list from an interstitial's close callback (§0.5).
 */
sealed interface BlurScanProgress {

    /** [done] of [total] photos measured. One emission per batch, never one per photo. */
    data class Scoring(val done: Int, val total: Int) : BlurScanProgress

    data class Done(
        /** One group per [BlurTier] that found members, in tier order. Never an empty group. */
        val groups: ImmutableList<PhotoGroup>,
        /**
         * Photos whose bitmap could not be decoded, and which are therefore in **no** tier.
         *
         * Carried rather than swallowed, for the reason `SimilarScanProgress.Done.skipped` is: a
         * failed measurement is not a measurement of zero. Scoring an unreadable file as `0.0`
         * would put every one of them in [BlurTier.VeryBlurry] — pre-selected for deletion, on this
         * screen — which is the competitor's `b0.h` defect with a worse ending.
         */
        val skipped: Int = 0,
    ) : BlurScanProgress

    /**
     * The scan could not run — a denied read, or a provider that returned no cursor at all.
     *
     * An arm rather than a thrown exception, because a `Flow` that throws reaches the screen through
     * `launchSafely`'s catch and arrives as `AppError.Unexpected`, losing the one thing the user
     * needed to know (that it was a permission).
     */
    data class Failed(val error: AppError) : BlurScanProgress
}
