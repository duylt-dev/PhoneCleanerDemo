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
 * The route carries **three scalars** (`groupKey`, `startIndex`, `source`); the photos come from the
 * `PhotoSessionStore` that `source` names, because a group is an unbounded scan result and
 * `LLM.md` §7.2 requires a session store for exactly that case — together with the session-lost
 * branch every such route owes.
 *
 * This screen serves two grids and holds **no** other trace of which: `source` is read once in the
 * ViewModel to pick a store and once to set [marksKeptPhoto], and nothing below reads it again.
 */
data class PhotoPreviewState(
    val photos: ImmutableList<Photo> = persistentListOf(),
    val index: Int = 0,
    val selectedIds: ImmutableSet<PhotoId> = persistentSetOf(),
    /** The store was empty — process death, or the grid cleared it. See §2.2. */
    val sessionLost: Boolean = false,
    /**
     * Whether this session has a "kept" member at all — true for a similar group, false for a blur
     * tier.
     *
     * It is on `State` and not a `when` in the composable because it is a **fact about the session**,
     * not a rendering choice. A similar group is a set of rivals whose opener survives by default, so
     * naming it is honest; a blur tier has no rival and no survivor — every row is pre-selected — so
     * badging its first row "Keeping" would tell the reader a photo is safe that is in fact ticked
     * for deletion. That is the one way this shared screen could actively mislead, so the flag is
     * carried rather than inferred from [index].
     */
    val marksKeptPhoto: Boolean = true,
) : UiState {
    val current: Photo? get() = photos.getOrNull(index)
    val isCurrentSelected: Boolean get() = current?.id?.let { it in selectedIds } == true

    /** The group's opener is the one kept by default (§1.4) — where the session has one at all. */
    val isCurrentKept: Boolean get() = marksKeptPhoto && index == 0
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

    /**
     * Session lost: pop back to the grid that opened this pager. The competitor's equivalent is a
     * bare `finish()`.
     *
     * Named for what it does and not for a destination: the pager is opened from two grids and pops
     * to whichever one is beneath it, so `NavigateToSimilar` became a name that was wrong half the
     * time. `:app` maps it to `popBackStack()` for both.
     */
    data object NavigateToGrid : PhotoPreviewEffect
}
