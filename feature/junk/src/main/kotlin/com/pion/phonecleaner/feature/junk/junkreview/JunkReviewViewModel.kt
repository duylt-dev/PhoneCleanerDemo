package com.pion.phonecleaner.feature.junk.junkreview

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.junk.JunkCategory
import com.pion.phonecleaner.domain.model.junk.JunkCategoryId
import com.pion.phonecleaner.domain.model.junk.JunkSession
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.JunkSessionStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableSet

/**
 * The review screen (`docs/screens/12-junk-cleaning.md` §4.2).
 *
 * **No `Job` field exists on this ViewModel.** The three tap intents are pure reducers: no repository
 * call, no coroutine, no dispatcher. There is nothing to cancel.
 *
 * The competitor's equivalent recomputes the CTA total by walking the whole tree on every tap and
 * then hides the cost behind a 50 ms `Handler` debounce (`VibrnancActivity.java:458-484`, Delta R3).
 */
class JunkReviewViewModel(
    private val session: JunkSessionStore,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<JunkReviewState, JunkReviewIntent, JunkReviewEffect>(JunkReviewState(), log) {

    /** §8.2 requires the missing-session hop to be raised once, not once per re-emission. */
    private var hasReportedMissingSession = false

    init {
        // `init` observes, it does not act (MVI §3 rule 3). `collectSafely`, never
        // `.onEach { }.launchIn(viewModelScope)` — which is the same missing exception handler
        // spelled so that a grep for `viewModelScope.launch` misses it (MVI §1).
        session.session.collectSafely { current -> onSession(current) }
    }

    override fun onIntent(intent: JunkReviewIntent) {
        when (intent) {
            is JunkReviewIntent.CategoryHeaderTapped -> toggleExpanded(intent.id)
            is JunkReviewIntent.CategoryCheckTapped -> toggleCategory(intent.id)
            is JunkReviewIntent.ItemCheckTapped -> toggleItem(intent.path)
            JunkReviewIntent.CleanTapped -> onCleanTapped()
            JunkReviewIntent.BackPressed -> sendEffect(JunkReviewEffect.NavigateBack)
        }
    }

    private fun onSession(current: JunkSession?) {
        if (current == null) {
            setState { copy(isSessionMissing = true) }
            if (!hasReportedMissingSession) {
                hasReportedMissingSession = true
                sendEffect(JunkReviewEffect.NavigateToScan)
            }
            return
        }
        // An immutable copy, not `categories.addAll(wc.g.b())`, which copies references so that the
        // screen and the static holder alias the same mutable nodes (Delta R10).
        setState { withSelection(current.categories, current.totalBytes, current.selectedPaths) }
    }

    private fun toggleExpanded(id: JunkCategoryId) {
        setState {
            val expanded = if (id in expandedCategories) expandedCategories - id else expandedCategories + id
            copy(expandedCategories = expanded.toImmutableSet())
        }
    }

    /**
     * A `Partial` category selects all, never deselects (§8.2). "Some are ticked" and "tick the rest"
     * is the reading a user expects; the competitor has no `Partial` to reason about at all.
     */
    private fun toggleCategory(id: JunkCategoryId) {
        setState {
            val category = categories.firstOrNull { it.id == id } ?: return@setState this
            val paths = category.items.map { it.path }
            val selected = if (checkStates[id] == CheckState.Checked) {
                selectedPaths - paths.toSet()
            } else {
                selectedPaths + paths
            }
            withSelection(categories, totalBytes, selected.toImmutableSet())
        }
    }

    private fun toggleItem(path: String) {
        setState {
            val selected = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
            withSelection(categories, totalBytes, selected.toImmutableSet())
        }
    }

    private fun onCleanTapped() {
        if (!currentState.canClean) return
        session.select(currentState.selectedPaths)
        // An event the competitor does not have at all: it has no analytics for "Clean Now" tapped,
        // none for completion, and none carrying a size (§5.5 Delta C13).
        analytics.track(
            AnalyticsEvent.CleanRequested(
                selectedBytes = currentState.selectedBytes,
                selectedCount = currentState.selectedCount,
            ),
        )
        sendEffect(JunkReviewEffect.NavigateToClean)
    }
}

/**
 * Recomputes [JunkReviewState.selectedBytes] and [JunkReviewState.checkStates] in ONE pass, once per
 * `setState`.
 *
 * This is the stored-not-derived deviation the contract documents: `checkState(id)` as a computed
 * `val` walks the whole category on every recomposition of a header, and there is a sticky header per
 * category.
 */
private fun JunkReviewState.withSelection(
    categories: ImmutableList<JunkCategory>,
    totalBytes: Long,
    selectedPaths: ImmutableSet<String>,
): JunkReviewState {
    var selectedBytes = 0L
    val checkStates = persistentMapOf<JunkCategoryId, CheckState>().builder()
    for (category in categories) {
        var selectedInCategory = 0
        for (item in category.items) {
            if (item.path in selectedPaths) {
                selectedInCategory++
                // The ORIGINAL size, always: `JunkItem.sizeBytes` is never zeroed to encode
                // "deselected", so nothing has to be remembered to undo it (Delta R1).
                selectedBytes += item.sizeBytes
            }
        }
        checkStates[category.id] = when {
            selectedInCategory == 0 -> CheckState.Unchecked
            selectedInCategory == category.items.size -> CheckState.Checked
            else -> CheckState.Partial
        }
    }
    return copy(
        categories = categories,
        totalBytes = totalBytes,
        selectedPaths = selectedPaths,
        selectedBytes = selectedBytes,
        checkStates = checkStates.build(),
        isSessionMissing = false,
    )
}
