package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import kotlinx.collections.immutable.ImmutableList

/**
 * The scan hand-off between the similar grid (§1) and the full-screen pager (§2)
 * (`docs/screens/13-photo-and-media.md` §2.2, §2.4).
 *
 * NAME — `docs/screens/13-photo-and-media.md` §8 item 2 records that the *pattern* is adjudicated
 * (`docs/system-architecture.md` §6.2 row 3) and that this identifier is the appendix's own; it is
 * carried unchanged rather than renamed, and it is declared exactly once, in `photoDataModule`.
 *
 * It replaces the first of this cluster's three static hand-offs: two fields on the receiving
 * Activity, with no extras on the `Intent` at all (§2.5). A static cannot survive process death and
 * cannot be typed — and `null` here is precisely the branch that detects it.
 *
 * Everything the pager reads is on [PhotoSessionStore]; the one member below is the one whose rule
 * is this feature's own.
 */
interface SimilarPhotoSessionStore : PhotoSessionStore {

    /**
     * Stores a finished scan. Pre-selection is applied here, once: every member of every group
     * **except the first**, which is the newest (§1.2). The competitor writes `isSelected` onto the
     * model inside the scan pipeline instead.
     */
    fun put(groups: ImmutableList<PhotoGroup>, skipped: Int = 0)
}
