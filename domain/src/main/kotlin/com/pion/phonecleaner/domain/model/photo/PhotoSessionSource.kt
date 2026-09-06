package com.pion.phonecleaner.domain.model.photo

import kotlinx.serialization.Serializable

/**
 * Which grid's scan the full-screen pager is showing.
 *
 * The pager is one screen serving two grids, and the two keep **separate**
 * `PhotoSessionStore` singles — a delete on one must not prune the other's groups. So the pager has
 * to be told which session it was opened over, and this is that argument.
 *
 * It travels as a route argument rather than being inferred from the back stack because the back
 * stack is not a fact a ViewModel may read: `PhotoPreviewViewModel` sees a `SavedStateHandle` and
 * nothing else (MVI §4). `@Serializable` for the same reason `JunkScanMode` and `PinMode` are —
 * type-safe navigation stores it under the route property's own name.
 */
@Serializable
enum class PhotoSessionSource {

    /** The similar-photo grid: groups of look-alikes, all but each group's opener pre-selected. */
    Similar,

    /** The blurry-photo grid: one group per blur tier, every row pre-selected. */
    Blurry,
}
