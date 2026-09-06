package com.pion.phonecleaner.feature.files.duplicates

import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.DuplicateGroup
import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The pure half of `duplicates`: state in, state out, no coroutine and no repository.
 *
 * Split out of the ViewModel so neither file exceeds the size rule (`LLM.md` §4) and so the
 * reducers are testable without constructing a ViewModel at all.
 */

/**
 * A fresh scan clears every number the previous one left, including `error` and `failedCount` —
 * both are re-entry guards, and a stale one makes a running scan look like a failed one.
 *
 * `filter` deliberately survives: the user narrowed the list to Images, and a rescan that silently
 * widened it back would look like the filter had been ignored.
 */
internal fun DuplicatesState.withScanStarted(): DuplicatesState = copy(
    phase = ToolPhase.Scanning,
    hashed = 0,
    candidates = 0,
    collected = 0,
    scanTruncated = false,
    failedCount = 0,
    error = null,
)

/** The whole progress fold, so the ViewModel names no arm of it. */
internal fun DuplicatesState.withScanProgress(
    progress: DuplicateScanProgress,
    restoredSelection: ImmutableSet<String>,
): DuplicatesState = when (progress) {
    is DuplicateScanProgress.Collecting -> copy(collected = progress.found)
    is DuplicateScanProgress.Hashing ->
        copy(hashed = progress.hashed, candidates = progress.candidates)

    is DuplicateScanProgress.Finished ->
        withScanFinished(progress.groups, progress.truncated, restoredSelection)
}

/**
 * The pre-selection happens **here**, when the groups arrive — not inside a hashing loop writing
 * `isSelected` onto the row model, which is the competitor's shape (§2.2).
 *
 * A selection restored from `SavedStateHandle` wins over the pre-selection when it is non-empty:
 * the user's own choice survives process death, and a stale id is dropped rather than sent to the
 * deleter.
 */
internal fun DuplicatesState.withScanFinished(
    groups: ImmutableList<DuplicateGroup>,
    truncated: Boolean,
    restoredSelection: ImmutableSet<String>,
): DuplicatesState {
    val present = groups.flatMapTo(mutableSetOf()) { group -> group.files.map(ScannedFile::id) }
    val restored = restoredSelection.filterTo(mutableSetOf(), present::contains)
    return copy(
        phase = ToolPhase.Completing,
        groups = groups,
        selectedIds = if (restored.isEmpty()) groups.olderIds() else restored.toImmutableSet(),
        scanTruncated = truncated,
    )
}

/** Every copy except the newest of each group — what "Select older copies" restores. */
internal fun ImmutableList<DuplicateGroup>.olderIds(): ImmutableSet<String> =
    flatMap { group -> group.files.filter { it.id != group.newestId }.map(ScannedFile::id) }
        .toImmutableSet()

/**
 * `Deleted` prunes the groups in the reducer and keeps `failedPaths` — a delete failure is
 * reported, never a toast and nothing else. A group left with one member is no longer a duplicate
 * and is dropped, which is why the list shrinks by more rows than were removed.
 */
internal fun DuplicatesState.withDeleted(outcome: DeleteOutcome.Deleted): DuplicatesState {
    val removed = outcome.ids.toSet()
    val remaining = groups.mapNotNull { group ->
        val kept = group.files.filterNot { it.id in removed }
        if (kept.size < 2) {
            null
        } else {
            // If the kept copy itself was removed, the group needs a new one, or `reclaimableBytes`
            // would count every survivor as reclaimable and promise bytes that do not exist.
            group.copy(files = kept.toImmutableList(), newestId = kept.newestIdOf(group.newestId))
        }
    }.toImmutableList()
    return copy(
        phase = ToolPhase.Ready,
        groups = remaining,
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it !in removed }.toImmutableSet(),
        previewingId = previewingId?.takeIf { it !in removed },
        failedCount = outcome.failedPaths.size,
    )
}

internal fun DuplicatesState.withToggled(id: String): DuplicatesState = copy(
    selectedIds = if (id in selectedIds) {
        (selectedIds - id).toImmutableSet()
    } else {
        (selectedIds + id).toImmutableSet()
    },
)

/** The previous keeper if it survived, otherwise the most recently modified survivor. */
private fun List<ScannedFile>.newestIdOf(previous: String): String =
    firstOrNull { it.id == previous }?.id
        ?: maxByOrNull(ScannedFile::lastModifiedAtMillis)?.id
        ?: previous
