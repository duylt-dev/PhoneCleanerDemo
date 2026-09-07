package com.pion.phonecleaner.feature.files.videocompressor

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.mvi.SelectableFiles
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.feature.files.component.MediaSort
import com.pion.phonecleaner.feature.files.component.VideoCandidateId
import com.pion.phonecleaner.feature.files.component.selectableVideos
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

/**
 * The pure half of `videocompressor`: state in, state out, no coroutine and no repository — split
 * out of the ViewModel so neither file exceeds `LLM.md` §4's size rule, mirroring
 * `VideoManagerReducers.kt`.
 */

/** Sorting is pure and happens here — no repository round trip, the same rule `video` follows. */
internal fun ImmutableList<VideoCandidate>.sortedByMedia(sort: MediaSort): ImmutableList<VideoCandidate> =
    when (sort) {
        MediaSort.NewestFirst -> sortedByDescending { it.file.lastModifiedAtMillis }
        MediaSort.LargestFirst -> sortedByDescending { it.sizeBytes }
        MediaSort.Name -> sortedBy { it.file.name.lowercase() }
    }.toImmutableList()

internal fun VideoCompressorState.withLoaded(
    loaded: ImmutableList<VideoCandidate>,
    restoredSelection: ImmutableSet<String>,
): VideoCompressorState {
    // A selection survives a reload only for rows that are still there — a stale id would be sent to
    // the run screen, which is how a run would reach a video the user never actually re-selected.
    val present = loaded.mapTo(mutableSetOf(), VideoCandidateId)
    val keep = (restoredSelection + videos.selectedIds).filterTo(mutableSetOf(), present::contains)
    return copy(
        phase = ToolPhase.Completing,
        videos = selectableVideos(loaded.sortedByMedia(sort), keep.toImmutableSet()),
    )
}

internal fun VideoCompressorState.withSort(sort: MediaSort): VideoCompressorState =
    copy(sort = sort, videos = videos.copy(items = videos.items.sortedByMedia(sort)))

/**
 * Every row *select all* offers — `alreadyCompressed` rows are kept out (D7). A deliberate individual
 * tap still selects one of those rows; this set is never used to filter what a tap may do.
 */
internal fun VideoCompressorState.selectableIds(): ImmutableSet<String> =
    videos.items.asSequence()
        .filterNot { it.alreadyCompressed }
        .mapTo(mutableSetOf(), VideoCandidateId)
        .toImmutableSet()

/**
 * D7's toggle, in one place: select everything in [selectable], or clear everything (including any
 * `alreadyCompressed` row selected by hand) if it is already fully covered.
 *
 * No `selectable.isNotEmpty()` guard. A list where every row is already compressed offers nothing to
 * add, so with the guard the control was drawn, enabled and unable to do anything at all — not even
 * clear the rows the user had picked by hand. `containsAll` of an empty set is vacuously true, which
 * is exactly the right answer: there is nothing left to select, so the toggle's meaning is "clear".
 */
internal fun SelectableFiles<VideoCandidate>.selectAllSkipping(
    selectable: ImmutableSet<String>,
): SelectableFiles<VideoCandidate> = if (selectedIds.containsAll(selectable)) {
    clearSelection()
} else {
    copy(selectedIds = (selectedIds + selectable).toImmutableSet())
}

/** An unknown restored name — a different app install, a stale key — falls back to `Default`. */
internal fun SavedStateHandle.restoredPreset(key: String): VideoQualityPreset =
    get<String>(key)?.let { name -> VideoQualityPreset.entries.firstOrNull { it.name == name } }
        ?: VideoQualityPreset.Default

internal fun SavedStateHandle.restoredCodec(key: String): VideoCodecOption =
    get<String>(key)?.let { name -> VideoCodecOption.entries.firstOrNull { it.name == name } }
        ?: VideoCodecOption.Default
