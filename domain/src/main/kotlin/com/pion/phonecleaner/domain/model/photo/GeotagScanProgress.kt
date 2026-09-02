package com.pion.phonecleaner.domain.model.photo

import com.pion.phonecleaner.core.common.error.AppError
import kotlinx.collections.immutable.ImmutableList

/**
 * What the EXIF location scan reports while it runs (`docs/screens/13-photo-and-media.md` §5.2).
 *
 * DELIBERATE DEVIATION from §0.3, which types `ExifRepository.photosWithLocation()` as a `suspend`
 * function returning the finished list. §5.2 of the same appendix requires the screen to report
 * `scanned`/`toScan` while the scan runs, and `PhotoPrivacyState` (§5.1) declares both fields. A
 * suspend function returning one value cannot fill them, and a state field nothing can ever fill is
 * worse than one more progress type — so this mirrors [SimilarScanProgress], which §0.3 defines for
 * exactly the same job on the sibling screen.
 */
sealed interface GeotagScanProgress {

    data class Scanning(val scanned: Int, val total: Int) : GeotagScanProgress

    data class Done(val groups: ImmutableList<PhotoGroup>) : GeotagScanProgress

    /** Same reason as `SimilarScanProgress.Failed`: a denied read is news, not an `Unexpected`. */
    data class Failed(val error: AppError) : GeotagScanProgress
}
