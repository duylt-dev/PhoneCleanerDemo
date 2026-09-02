package com.pion.phonecleaner.feature.files.bigfiles

import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileScanProgress
import com.pion.phonecleaner.feature.files.component.selectableFiles
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The pure half of `bigfiles`: state in, state out, no coroutine and no repository.
 *
 * Split out of the ViewModel so neither file exceeds the size rule (`LLM.md` §4) and so the reducers
 * are testable without constructing a ViewModel at all.
 */
internal fun BigFilesState.withScanFinished(
    progress: FileScanProgress.Finished,
    restoredSelection: ImmutableSet<String>,
): BigFilesState {
    // A selection survives a re-scan only for rows that are still there — a stale id would be sent
    // to the deleter, which is how a delete hits a row the user never selected.
    val present = progress.files.mapTo(mutableSetOf()) { it.id }
    val keep = (restoredSelection + files.selectedIds).filterTo(mutableSetOf(), present::contains)
    return copy(
        phase = ToolPhase.Completing,
        files = selectableFiles(progress.files, keep.toImmutableSet()),
        coverage = progress.coverage,
        scanTruncated = progress.truncated,
    )
}

/**
 * `Deleted` prunes the list in the reducer and keeps `failedPaths` — a delete failure is reported,
 * never a toast and nothing else. `File.delete()` fails routinely under scoped storage.
 */
internal fun BigFilesState.withDeleted(outcome: DeleteOutcome.Deleted): BigFilesState {
    val removed = outcome.ids.toSet()
    return copy(
        phase = ToolPhase.Ready,
        files = selectableFiles(files.items.filterNot { it.id in removed }.toImmutableList()),
        failedCount = outcome.failedPaths.size,
    )
}
