package com.pion.phonecleaner.feature.photo.preview

import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `preview` — `docs/screens/13-photo-and-media.md` §2.1. Replaces `RelevhosActivity` (250 L) and the
 * first of the cluster's three static hand-offs.
 *
 * The route carries **two scalars** (`groupKey`, `startIndex`); the photos come from
 * `SimilarPhotoSessionStore`, because a group is an unbounded scan result and `LLM.md` §7.2 requires
 * a session store for exactly that case — together with the session-lost branch every such route
 * owes.
 */
data class PhotoPreviewState(
    val photos: ImmutableList<Photo> = persistentListOf(),
    val index: Int = 0,
    val selectedIds: ImmutableSet<PhotoId> = persistentSetOf(),
    /** The store was empty — process death, or the grid cleared it. See §2.2. */
    val sessionLost: Boolean = false,
) : UiState {
    val current: Photo? get() = photos.getOrNull(index)
    val isCurrentSelected: Boolean get() = current?.id?.let { it in selectedIds } == true

    /** The group's opener is the one kept by default (§1.4). */
    val isCurrentKept: Boolean get() = index == 0
    val counterPosition: Int get() = index + 1
    val counterTotal: Int get() = photos.size
}

sealed interface PhotoPreviewIntent : UiIntent {
    data object ScreenStarted : PhotoPreviewIntent
    data class PageChanged(val index: Int) : PhotoPreviewIntent
    data object SelectionToggled : PhotoPreviewIntent
    data object ClosePressed : PhotoPreviewIntent
}

sealed interface PhotoPreviewEffect : UiEffect {
    data object NavigateBack : PhotoPreviewEffect

    /** Session lost: pop back to the scan. The competitor's equivalent is a bare `finish()`. */
    data object NavigateToSimilar : PhotoPreviewEffect
}
