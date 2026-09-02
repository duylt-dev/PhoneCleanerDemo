package com.pion.phonecleaner.domain.model.photo

import com.pion.phonecleaner.core.common.error.AppError
import kotlinx.collections.immutable.ImmutableList

/**
 * What the similar-photo scan reports while it runs
 * (`docs/screens/13-photo-and-media.md` §0.3).
 *
 * Real counts, emitted as the work happens. This is what the competitor's 4 000 ms Lottie was
 * covering for: `delay(od.q0.a(t0))` pads every scan to a fixed floor and the list is then revealed
 * by an **interstitial's close callback** (§0.5). Progress that is measured cannot lie about how far
 * along it is, and a list that is revealed by a phase cannot be held hostage by an SDK that never
 * calls back.
 */
sealed interface SimilarScanProgress {

    data class Hashing(val done: Int, val total: Int) : SimilarScanProgress

    data class Done(
        val groups: ImmutableList<PhotoGroup>,
        /**
         * Photos whose bitmap could not be decoded, and which are therefore in **no** group.
         *
         * Carried rather than swallowed because the alternative is the competitor's: `b0.h` returns
         * `0L` for a null bitmap, so every unreadable file collapses into a single "similar" group
         * (§1.4). A hash failure is not a similarity, and a count the user can see is the honest
         * form of "we could not look at these".
         */
        val skipped: Int = 0,
    ) : SimilarScanProgress

    /**
     * The scan could not run — a denied read, or a provider that returned no cursor at all.
     *
     * An arm rather than a thrown exception, because a `Flow` that throws reaches the screen through
     * `launchSafely`'s catch and arrives as `AppError.Unexpected`, losing the one thing the user
     * needed to know (that it was a permission). §3.5 records that this cluster's competitor has no
     * error state anywhere: a `MediaStore` exception propagates out of an unhandled `viewModelScope`.
     */
    data class Failed(val error: AppError) : SimilarScanProgress
}
