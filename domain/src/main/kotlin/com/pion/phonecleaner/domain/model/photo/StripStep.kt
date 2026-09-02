package com.pion.phonecleaner.domain.model.photo

/**
 * One photo's location strip (`docs/screens/13-photo-and-media.md` §0.3).
 *
 * [failed] exists because the competitor's engine `nd/e.a` catches every exception, prints it, and
 * **still counts the row as a success** — which is what makes its own "Delete Failed" toast
 * unreachable (§5.5).
 */
data class StripStep(
    val index: Int,
    val total: Int,
    val id: PhotoId,
    val failed: Boolean,
)
