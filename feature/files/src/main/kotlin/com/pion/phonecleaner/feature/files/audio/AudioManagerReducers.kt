package com.pion.phonecleaner.feature.files.audio

import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.feature.files.component.MediaAccess
import com.pion.phonecleaner.feature.files.component.MediaSort
import com.pion.phonecleaner.feature.files.component.selectableFiles
import com.pion.phonecleaner.feature.files.component.sortedBy
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The pure half of `audio`: state in, state out, no coroutine and no repository. Split out so neither
 * file exceeds the size rule (`LLM.md` §4) and so the reducers are testable without a ViewModel.
 */
internal fun AudioManagerState.withLoaded(
    loaded: ImmutableList<ScannedFile>,
    restoredSelection: ImmutableSet<String>,
): AudioManagerState {
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
internal fun AudioManagerState.withSort(sort: MediaSort): AudioManagerState =
    copy(sort = sort, files = files.copy(items = files.items.sortedBy(sort)))

/**
 * `Deleted` prunes the list here and keeps `failedPaths` — a delete failure is reported, never a
 * toast and nothing else.
 */
internal fun AudioManagerState.withDeleted(outcome: DeleteOutcome.Deleted): AudioManagerState {
    val removed = outcome.ids.toSet()
    return copy(
        phase = ToolPhase.Ready,
        files = selectableFiles(files.items.filterNot { it.id in removed }.toImmutableList()),
        failedCount = outcome.failedPaths.size,
    )
}

/**
 * **The `Partial` arm is written out and does nothing, deliberately** (§4).
 *
 * `READ_MEDIA_AUDIO` has no `READ_MEDIA_VISUAL_USER_SELECTED` counterpart, so a partial grant cannot
 * occur on this screen. Saying so with a named arm — rather than letting a silent `else` absorb it —
 * is what makes the day the platform adds one a compile-time question instead of a wrong list.
 */
internal fun MediaAccess.audioCanLoad(): Boolean = when (this) {
    MediaAccess.Granted -> true
    MediaAccess.Partial -> false // unreachable for audio: there is no user-selected audio grant
    MediaAccess.Denied, MediaAccess.Unknown -> false
}
