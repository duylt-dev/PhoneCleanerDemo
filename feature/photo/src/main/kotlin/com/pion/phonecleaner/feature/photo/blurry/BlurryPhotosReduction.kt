package com.pion.phonecleaner.feature.photo.blurry

import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.feature.photo.allIds
import com.pion.phonecleaner.feature.photo.toggleGroup

/**
 * The pure arithmetic of a delete, split out of `BlurryPhotosViewModel` so it is a plain unit test
 * with no fakes and no dispatcher (`LLM.md` §9) — the same split `SimilarPhotosReduction` makes.
 *
 * The bridge these functions cross is the one `PhotoRepository.delete` documents: `DeleteOutcome`
 * speaks in `ScannedFile.id`, which the mapping sets to `Photo.contentUri`. So the screen prunes
 * with `photo.contentUri in outcome.ids` — no parsing, and no `android.net.Uri` anywhere near a
 * ViewModel.
 */
internal fun BlurryPhotosState.idsForUris(uris: Set<String>): Set<PhotoId> =
    groups.asSequence()
        .flatMap { it.photos.asSequence() }
        .filter { it.contentUri in uris }
        .map { it.id }
        .toSet()

/**
 * What the rows still on screen weigh. Used only on the consent-granted path, where the system has
 * already removed them and there is nothing left to ask.
 */
internal fun BlurryPhotosState.bytesForUris(uris: Set<String>): Long =
    groups.asSequence()
        .flatMap { it.photos.asSequence() }
        .filter { it.contentUri in uris }
        .sumOf { it.sizeBytes }

/** `freedBytes` is measured. `NothingFound` when nothing was actually reclaimed. */
internal fun blurryCleanupSummary(freedBytes: Long, itemCount: Int): CleanupSummary = CleanupSummary(
    feature = FeatureId.BlurryPhotos,
    freedBytes = freedBytes,
    itemCount = itemCount,
    outcome = if (freedBytes > 0L) CleanupOutcome.Cleaned else CleanupOutcome.NothingFound,
)

/**
 * The selection after the screen-wide select-all: clear it when everything is already selected,
 * otherwise take everything.
 */
internal fun BlurryPhotosState.selectionAfterSelectAll(): Set<PhotoId> {
    val everything = allIds(groups)
    return if (selectedIds.size == everything.size) emptySet() else everything
}

/**
 * The selection after one tier's header checkbox, or `null` when [groupKey] names no tier on screen
 * — in which case the caller must leave the selection alone rather than clear it.
 */
internal fun BlurryPhotosState.selectionAfterTierToggle(groupKey: String): Set<PhotoId>? {
    val group = groups.firstOrNull { it.key == groupKey } ?: return null
    return selectedIds.toggleGroup(group)
}

/**
 * The `(groupKey, index)` the pager opens at, or `null` when [id] is on no group.
 *
 * The pair is built here rather than in the cell callback because a per-item `(key, index)` lambda
 * can only be written inside `items {}`, which `LLM.md` §8 bans: it is a new instance on every
 * recomposition and defeats the skip for the whole lane.
 */
internal fun BlurryPhotosState.previewTarget(id: PhotoId): Pair<String, Int>? {
    val group = groups.firstOrNull { g -> g.photos.any { it.id == id } } ?: return null
    return group.key to group.photos.indexOfFirst { it.id == id }.coerceAtLeast(0)
}
