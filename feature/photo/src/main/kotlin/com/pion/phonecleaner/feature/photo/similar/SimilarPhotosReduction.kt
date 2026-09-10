package com.pion.phonecleaner.feature.photo.similar

import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.photo.PhotoId

/**
 * The pure arithmetic of a delete, split out of `SimilarPhotosViewModel` so it is a plain unit test
 * with no fakes and no dispatcher (`LLM.md` §9).
 *
 * The bridge these three functions cross is the one `PhotoRepository.delete` documents:
 * `DeleteOutcome` speaks in `ScannedFile.id`, which the mapping sets to `Photo.contentUri`. So the
 * screen prunes with `photo.contentUri in outcome.ids` — no parsing, and no `android.net.Uri`
 * anywhere near a ViewModel.
 */
internal fun SimilarPhotosState.idsForUris(uris: Set<String>): Set<PhotoId> =
    groups.asSequence()
        .flatMap { it.photos.asSequence() }
        .filter { it.contentUri in uris }
        .map { it.id }
        .toSet()

/**
 * What the rows still on screen weigh. Used only on the consent-granted path, where the system has
 * already removed them and there is nothing left to ask.
 */
internal fun SimilarPhotosState.bytesForUris(uris: Set<String>): Long =
    groups.asSequence()
        .flatMap { it.photos.asSequence() }
        .filter { it.contentUri in uris }
        .sumOf { it.sizeBytes }

/**
 * `freedBytes` is measured. `NothingFound` when nothing was actually reclaimed; `MovedToTrash` when
 * it was and [recoverable] says the bytes went into the bin, not off the device (plan
 * `260908-0801-trash-bin`, Phase 07).
 */
internal fun similarCleanupSummary(
    freedBytes: Long,
    itemCount: Int,
    recoverable: Boolean,
): CleanupSummary = CleanupSummary(
    feature = FeatureId.SimilarPhotos,
    freedBytes = freedBytes,
    itemCount = itemCount,
    outcome = when {
        freedBytes <= 0L -> CleanupOutcome.NothingFound
        recoverable -> CleanupOutcome.MovedToTrash
        else -> CleanupOutcome.Cleaned
    },
)
