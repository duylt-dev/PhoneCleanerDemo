package com.pion.phonecleaner.feature.files.video

import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.feature.files.component.MediaSort
import com.pion.phonecleaner.feature.files.component.selectableFiles
import com.pion.phonecleaner.feature.files.component.sortedBy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The pure half of `video`: state in, state out, no coroutine and no repository.
 *
 * Split out of the ViewModel so neither file exceeds the size rule (`LLM.md` §4) and so the reducers
 * are testable without constructing a ViewModel at all.
 */
internal fun VideoManagerState.withLoaded(
    loaded: ImmutableList<ScannedFile>,
    restoredSelection: ImmutableSet<String>,
): VideoManagerState {
    // A selection survives a reload only for rows that are still there — a stale id would be sent to
    // the deleter, which is how a delete hits a row the user never selected.
    val present = loaded.mapTo(mutableSetOf()) { it.id }
    val keep = (restoredSelection + files.selectedIds).filterTo(mutableSetOf(), present::contains)
    return copy(
        phase = ToolPhase.Completing,
        files = selectableFiles(loaded.sortedBy(sort), keep.toImmutableSet()),
    )
}

/** Sorting is pure and happens here — no repository round trip (§3.2). */
internal fun VideoManagerState.withSort(sort: MediaSort): VideoManagerState =
    copy(sort = sort, files = files.copy(items = files.items.sortedBy(sort)))

/**
 * `Deleted` prunes the list in the reducer and keeps `failedPaths` — a delete failure is reported,
 * never a toast and nothing else.
 */
internal fun VideoManagerState.withDeleted(outcome: DeleteOutcome.Deleted): VideoManagerState {
    val removed = outcome.ids.toSet()
    return copy(
        phase = ToolPhase.Ready,
        files = selectableFiles(files.items.filterNot { it.id in removed }.toImmutableList()),
        failedCount = outcome.failedPaths.size,
    )
}
