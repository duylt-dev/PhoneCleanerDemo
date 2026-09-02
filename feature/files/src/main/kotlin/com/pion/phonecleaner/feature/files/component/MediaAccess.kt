package com.pion.phonecleaner.feature.files.component

import com.pion.phonecleaner.domain.model.file.ScannedFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The two enums the media tools share, and the pure sort that goes with them.
 *
 * They live in the cluster's `component/` package rather than in one screen's contract because
 * `video` and `audio` both read them and `LLM.md` §3.7 makes this the only place a thing shared by
 * two screens of one cluster can go without being promoted to `:core:ui`.
 */
enum class MediaSort {
    NewestFirst,
    LargestFirst,
    Name,
}

/**
 * How much of a media collection this app may see.
 *
 * **[Partial] is Android 14's `READ_MEDIA_VISUAL_USER_SELECTED`**, and it is a normal outcome, not a
 * failure (`docs/system-architecture.md` §8.3). It has no audio equivalent — `READ_MEDIA_AUDIO` has
 * no user-selected variant — so the audio screen states that explicitly rather than letting a silent
 * `else` decide.
 *
 * The competitor has no permission state at all: it gates the whole screen from outside and never
 * starts the Activity if the check fails, so partial access can only ever be an all-or-nothing
 * decision taken before the screen exists.
 */
enum class MediaAccess {
    Unknown,
    Granted,
    Partial,
    Denied,
    ;

    val canLoad: Boolean get() = this == Granted || this == Partial
}

/**
 * Sorting is **pure and happens in the reducer** — no repository round trip
 * (`docs/screens/14-file-tools-and-app-manager.md` §3.2). The competitor has no sort at all; its
 * query order is fixed.
 */
internal fun ImmutableList<ScannedFile>.sortedBy(sort: MediaSort): ImmutableList<ScannedFile> =
    when (sort) {
        MediaSort.NewestFirst -> sortedByDescending(ScannedFile::lastModifiedAtMillis)
        MediaSort.LargestFirst -> sortedByDescending(ScannedFile::sizeBytes)
        MediaSort.Name -> sortedBy { it.name.lowercase() }
    }.toImmutableList()
