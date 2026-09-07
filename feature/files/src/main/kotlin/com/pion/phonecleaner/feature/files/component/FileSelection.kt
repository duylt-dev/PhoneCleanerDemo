package com.pion.phonecleaner.feature.files.component

import com.pion.phonecleaner.core.mvi.SelectableFiles
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The identity every file list in this cluster is keyed by, held as **one instance**.
 *
 * `SelectableFiles<T>` takes `idOf` as a constructor property, so it participates in the data class's
 * `equals`. Written inline at five call sites it would be five lambdas, and a state object that is
 * never `equals` its predecessor is a screen that can never skip (`LLM.md` §8). One `val` is one
 * instance for the process.
 *
 * `ScannedFile.id` — not the path — is the identity: it is what a selection, a delete request and a
 * `LazyColumn` key all carry, and `DeleteOutcome.Deleted.ids` is a list of exactly these.
 */
internal val ScannedFileId: (ScannedFile) -> String = ScannedFile::id

/** `SelectableFiles(items, selected, ScannedFileId)`, so no screen re-states the identity. */
internal fun selectableFiles(
    items: ImmutableList<ScannedFile> = persistentListOf(),
    selectedIds: ImmutableSet<String> = persistentSetOf(),
): SelectableFiles<ScannedFile> = SelectableFiles(items, selectedIds, ScannedFileId)

/**
 * The same identity rule for the video compressor's rows, held as **one instance** for the same
 * reason [ScannedFileId] is: `idOf` is a constructor property of `SelectableFiles`, so a lambda
 * written at the call site makes every new state object unequal to its predecessor, and a screen
 * that is never `equals` is a screen that can never skip recomposition (`LLM.md` §8).
 *
 * `VideoCandidate.id` is the identity because it is what a selection, a `LazyColumn` key and the
 * `OpenVideoCompressRun` effect payload all carry.
 */
internal val VideoCandidateId: (VideoCandidate) -> String = VideoCandidate::id

/** `SelectableFiles(items, selected, VideoCandidateId)`, so no screen re-states the identity. */
internal fun selectableVideos(
    items: ImmutableList<VideoCandidate> = persistentListOf(),
    selectedIds: ImmutableSet<String> = persistentSetOf(),
): SelectableFiles<VideoCandidate> = SelectableFiles(items, selectedIds, VideoCandidateId)
