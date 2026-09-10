package com.pion.phonecleaner.domain.model.photo

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * A finished photo scan, and the selection over it, as one immutable value shared by a grid and the
 * full-screen pager (`docs/screens/13-photo-and-media.md` §2.2, §2.4).
 *
 * `LLM.md` §7.2 row 3: a payload that is a scan result of unbounded size travels through a session
 * store, not through a route argument — and **every session-store route owes a "session lost to
 * process death" branch**, which `PhotoPreviewState.sessionLost` is.
 *
 * It replaces two static fields on the receiving Activity plus the mutation of
 * `Likesat.isSelected` on the shared instances, which is how the competitor synchronises the pager
 * back to the grid — a mechanism that works only because both screens hold the same objects (§2.5).
 *
 * **It was named `SimilarPhotoSession` until the blurry-photo grid was added.** Nothing on it was
 * ever specific to similarity: it is groups, a selection and a skipped count. The two things that
 * *are* per-feature — which rows arrive pre-selected, and whether a group that falls to one member
 * survives — live in the [com.pion.phonecleaner.domain.repository.PhotoSessionStore]
 * implementations, which is why one pager can serve both grids without a `when` over the feature.
 */
data class PhotoSession(
    val groups: ImmutableList<PhotoGroup>,
    val selectedIds: ImmutableSet<PhotoId>,
    /** Photos that could not be decoded and are in no group. See `SimilarScanProgress.Done`. */
    val skipped: Int = 0,
)
