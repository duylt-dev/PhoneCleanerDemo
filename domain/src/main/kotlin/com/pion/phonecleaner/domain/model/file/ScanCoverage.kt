package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Which storage surfaces a scan was actually able to read.
 *
 * `docs/system-architecture.md` §8.4 makes this **mandatory for the default branch**: the competitor's
 * file scan silently degrades to installed packages only and reports nothing about it, so a partial
 * answer is presented as a complete one. A scan result that cannot say what it did *not* look at is
 * the defect this type removes.
 *
 * UNKNOWN — the type name is `docs/screens/14-file-tools-and-app-manager.md` §10 item 2's own
 * ("the requirement is adjudicated; no report names the type"), and no source fixes its shape.
 *
 * A SECOND TYPE OF THIS NAME EXISTS — `domain/model/security/ScanCoverage.kt`, a three-constant enum
 * the antivirus cluster wrote for the same requirement, whose own KDoc anticipates this one: *"the
 * file-tools cluster may define a richer type of the same name in its own package; that is a
 * duplication to resolve when both land"*. It is reported, not resolved here: collapsing them means
 * deciding whether a file tool's covered-surface list and a reputation scan's app/file split are one
 * concept, and neither appendix settles that.
 *
 * [surfaces] carries the machine identifiers `StorageRootProvider.coveredSurfaces()` returns —
 * `media:images`, `app:cache`, `tree:content://…`. They are **never rendered raw**: that provider's
 * own KDoc records that nothing in the corpus fixes the token format and that `:core:ui` owes the
 * mapping to a localised string, exactly as `ErrorMessages.kt` maps `AppError`. Until that mapping
 * exists a screen may render the *count*, and the banner it draws is driven by [isPartial] alone.
 */
data class ScanCoverage(
    val surfaces: ImmutableList<String> = persistentListOf(),
    /**
     * True when the grant in force cannot reach everything the tool claims to cover, so the screen
     * must offer a way to widen it rather than implying the list is complete.
     */
    val isPartial: Boolean = false,
) {
    val surfaceCount: Int get() = surfaces.size

    companion object {
        /** Nothing has scanned yet. Not "complete" — the screen renders no coverage claim at all. */
        val Unknown = ScanCoverage()
    }
}
