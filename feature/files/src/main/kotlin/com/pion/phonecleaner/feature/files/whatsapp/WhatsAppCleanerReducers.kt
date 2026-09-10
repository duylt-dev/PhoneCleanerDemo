package com.pion.phonecleaner.feature.files.whatsapp

import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.FileCleanProgress
import com.pion.phonecleaner.domain.model.file.WhatsAppBucket
import com.pion.phonecleaner.domain.model.file.WhatsAppBucketId
import com.pion.phonecleaner.feature.files.R
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The pure half of `whatsapp`: state in, state out, no coroutine and no repository.
 */

/**
 * One bucket landed. Tiles fill in one at a time instead of all six appearing after a 4 000 ms
 * floor, and a **non-empty** bucket is pre-selected **here** — the competitor re-evaluates that flag
 * inside its per-file loop (§6.2).
 */
internal fun WhatsAppCleanerState.withBucketFinished(bucket: WhatsAppBucket): WhatsAppCleanerState {
    val merged = (buckets.filterNot { it.id == bucket.id } + bucket)
        .sortedBy { it.id.ordinal }
        .toImmutableList()
    return copy(
        buckets = merged,
        selected = if (bucket.isEmpty) selected else (selected + bucket.id).toImmutableSet(),
    )
}

internal fun WhatsAppCleanerState.withBucketToggled(id: WhatsAppBucketId): WhatsAppCleanerState =
    copy(
        selected = if (id in selected) {
            (selected - id).toImmutableSet()
        } else {
            (selected + id).toImmutableSet()
        },
    )

/** Every non-empty bucket, or none. Selecting an empty bucket would promise zero bytes. */
internal fun WhatsAppCleanerState.withAllBucketsToggled(): WhatsAppCleanerState {
    val fillable = buckets.filterNot { it.isEmpty }.map { it.id }
    return copy(
        selected = if (selected.containsAll(fillable)) {
            persistentSetOf()
        } else {
            fillable.toImmutableSet()
        },
    )
}

/**
 * One deletion batch. `remainingBytes` moves because bytes were freed, and `failedCount` reaches the
 * state: `File.delete()` fails routinely under scoped storage and the competitor hard-codes its
 * outcome to `true` (§6.5).
 */
internal fun WhatsAppCleanerState.withCleanProgress(
    progress: FileCleanProgress,
): WhatsAppCleanerState {
    val current = cleaning ?: return this
    val next = when (progress) {
        is FileCleanProgress.Deleted -> current.copy(
            deletedBytes = current.deletedBytes + progress.freedBytes,
            deletedCount = current.deletedCount + progress.ids.size,
        )

        is FileCleanProgress.Failed ->
            current.copy(failedCount = current.failedCount + progress.paths.size)

        is FileCleanProgress.Finished -> current.copy(
            deletedBytes = progress.freedBytes,
            deletedCount = progress.deletedCount,
            failedCount = progress.failedCount,
            recoverable = progress.recoverable,
        )

        // Handled as an Effect by the ViewModel; the counters do not move on a consent request.
        is FileCleanProgress.NeedsConsent -> current
    }
    return copy(cleaning = next)
}

/** Files Android confirmed are gone leave their bucket, so the tiles show what is left. */
internal fun WhatsAppCleanerState.withDeleted(ids: Set<String>): WhatsAppCleanerState {
    if (ids.isEmpty()) return this
    val remaining = buckets.map { bucket ->
        bucket.copy(files = bucket.files.filterNot { it.id in ids }.toImmutableList())
    }.toImmutableList()
    return copy(
        buckets = remaining,
        selected = remaining.filterNot { it.isEmpty }.map { it.id }.toImmutableSet(),
    )
}

/** `MovedToTrash` only once something was actually deleted (plan `260908-0801-trash-bin`, Phase 07). */
internal fun WhatsAppCleanerState.cleanSummary(): CleanupSummary {
    val progress = cleaning
    return CleanupSummary(
        feature = FeatureId.WhatsAppCleaner,
        freedBytes = progress?.deletedBytes ?: 0L,
        itemCount = progress?.deletedCount ?: 0,
        outcome = when {
            (progress?.deletedCount ?: 0) == 0 -> CleanupOutcome.NothingFound
            progress?.recoverable == true -> CleanupOutcome.MovedToTrash
            else -> CleanupOutcome.Cleaned
        },
    )
}

/**
 * Every sibling tool confirms before deleting; this one deletes chat media (§6.5). [trashEligible] is
 * the confirmed operation mode. The use case never downgrades a refused move to permanent deletion.
 */
internal fun cleanConfirmSpec(fileCount: Int, trashEligible: Boolean): ConfirmSpec = ConfirmSpec(
    titleRes = R.string.whatsapp_confirm_title,
    bodyRes = if (trashEligible) R.plurals.whatsapp_confirm_trash_body else R.plurals.whatsapp_confirm_body,
    count = fileCount,
    confirmRes = com.pion.phonecleaner.core.ui.R.string.action_delete,
)

/** Stopping says plainly what stays removed, because nothing that is gone comes back. */
internal fun stopConfirmSpec(deletedCount: Int): ConfirmSpec = ConfirmSpec(
    titleRes = R.string.whatsapp_stop_title,
    bodyRes = R.plurals.whatsapp_stop_body,
    count = deletedCount,
    confirmRes = R.string.whatsapp_stop_confirm,
)
