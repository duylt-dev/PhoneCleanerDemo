package com.pion.phonecleaner.domain.model.photo

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * The similar-photo scan result, and the selection over it, as one immutable value shared by the
 * grid (§1) and the pager (§2) — `docs/screens/13-photo-and-media.md` §2.2, §2.4.
 *
 * `LLM.md` §7.2 row 3: a payload that is a scan result of unbounded size travels through a session
 * store, not through a route argument — and **every session-store route owes a "session lost to
 * process death" branch**, which `PhotoPreviewState.sessionLost` is.
 *
 * It replaces two static fields on the receiving Activity plus the mutation of
 * `Likesat.isSelected` on the shared instances, which is how the competitor synchronises the pager
 * back to the grid — a mechanism that works only because both screens hold the same objects (§2.5).
 */
data class SimilarPhotoSession(
    val groups: ImmutableList<PhotoGroup>,
    val selectedIds: ImmutableSet<PhotoId>,
    /** Photos that could not be decoded and are in no group. See `SimilarScanProgress.Done`. */
    val skipped: Int = 0,
)
