package com.pion.phonecleaner.feature.junk.junkreview

import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.junk.JunkCategory
import com.pion.phonecleaner.domain.model.junk.JunkCategoryId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * The review screen's contract (`docs/screens/12-junk-cleaning.md` §4.1).
 *
 * The screen is `junkreview`, never "JunkResult": `:feature:cleanresult` already owns the word
 * "result" (`docs/system-architecture.md` §3E).
 *
 * Nine `VibrnancActivity` fields plus six `yc.e` adapter fields collapse into the seven below. Four
 * of them are deleted outright: a 50 ms CTA `Handler` debounce that existed only because the total
 * was recomputed by walking the whole tree per tap; a `lastCategoryCheckState` cache plus
 * `invalidateItemDecorations()`; a "tick zero-size categories anyway" flag; and the two
 * original-size snapshot maps, which exist only to undo the model mutation of Delta R1.
 */
enum class CheckState {
    Checked,
    Unchecked,

    /**
     * The value the competitor does not have. `yc/e.java:302-333` treats a category as checked only
     * when *every* child has `size > 0`, so deselecting one item out of four hundred makes the
     * header read as "nothing selected" (Delta R2).
     */
    Partial,
}

data class JunkReviewState(
    val categories: ImmutableList<JunkCategory> = persistentListOf(),
    val totalBytes: Long = 0L,
    val expandedCategories: ImmutableSet<JunkCategoryId> = persistentSetOf(),

    /** Selection, explicit. Never `size = 0` written into the model (Delta R1, `LLM.md` §8). */
    val selectedPaths: ImmutableSet<String> = persistentSetOf(),

    /**
     * **Stored, not `get()`-computed — the one deliberate deviation from "derive, never store"**
     * in this cluster, and the profiling exception MVI §2 allows.
     *
     * `r2-03` §B2 defines both this and [selectedBytes] as computed `val`s and then notes in its own
     * §B2.3 that `checkState(id)` walks the whole category on every recomposition of a header.
     * Recomputing them once per `setState`, in the reducer, is cheaper and keeps the composable a
     * pure read.
     *
     * NOTE for `compose-stability.conf`: `ImmutableList` and `ImmutableSet` are listed there;
     * `ImmutableMap` is not. Until that line is added, this field makes the state unstable and the
     * screen cannot skip. The line is reported for the file's owner — it is not this cluster's to
     * edit (`LLM.md` §8).
     */
    val checkStates: ImmutableMap<JunkCategoryId, CheckState> = persistentMapOf(),

    /** Recomputed once per `setState`. See [checkStates]. */
    val selectedBytes: Long = 0L,

    /** The process-death branch every session-store route owes (`docs/system-architecture.md` §5.2). */
    val isSessionMissing: Boolean = false,
) : UiState {
    val selectedCount: Int get() = selectedPaths.size
    val isEmpty: Boolean get() = totalBytes == 0L
    val canClean: Boolean get() = selectedBytes > 0L
}

sealed interface JunkReviewIntent : UiIntent {
    /** Expand or collapse. */
    data class CategoryHeaderTapped(val id: JunkCategoryId) : JunkReviewIntent

    /** Select or deselect the whole category. */
    data class CategoryCheckTapped(val id: JunkCategoryId) : JunkReviewIntent

    data class ItemCheckTapped(val path: String) : JunkReviewIntent
    data object CleanTapped : JunkReviewIntent
    data object BackPressed : JunkReviewIntent
}

sealed interface JunkReviewEffect : UiEffect {
    /** The selection is in `JunkSessionStore`; this route carries nothing. */
    data object NavigateToClean : JunkReviewEffect
    data object NavigateBack : JunkReviewEffect

    /** The session was lost to process death — the case the competitor cannot even detect. */
    data object NavigateToScan : JunkReviewEffect
}
