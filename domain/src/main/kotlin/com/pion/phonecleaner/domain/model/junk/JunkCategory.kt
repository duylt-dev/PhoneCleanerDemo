package com.pion.phonecleaner.domain.model.junk

import kotlinx.collections.immutable.ImmutableList

/**
 * One section of the review screen: a category id, its items and its total.
 *
 * [totalBytes] is precomputed by the mapper and never summed during composition. The competitor
 * recomputes the equivalent by walking the whole tree on every tick and then hides the cost behind a
 * 50 ms `Handler` debounce (`VibrnancActivity.java:458-484`,
 * `docs/screens/12-junk-cleaning.md` §4.4 Delta R3).
 */
data class JunkCategory(
    val id: JunkCategoryId,
    val items: ImmutableList<JunkItem>,
    val totalBytes: Long,
)
