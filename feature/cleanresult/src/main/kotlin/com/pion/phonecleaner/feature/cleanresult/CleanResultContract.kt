package com.pion.phonecleaner.feature.cleanresult

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The contract of the ONE result route that serves fifteen features
 * (`docs/screens/14-file-tools-and-app-manager.md` §8).
 *
 * It replaces `OffertilActivity` **and** `DisobsquActivity`. What that one move deletes: a
 * destination whose Back key had to be blocked with a toast and which had no back arrow at all; a
 * `ValueAnimator` `onDestroy` failed to cancel, which then navigated from a finished Activity; a
 * `Float.parseFloat("null")` crash path; a second copy of the `fileSize`/`fileUnit` extras; and two
 * disagreeing `when (goTag)` blocks whose id sets had already drifted.
 *
 * **The argument is [CleanupSummary], not an `Int` tag and not a formatted string.** `freedBytes` is
 * a raw `Long` the whole way — `ByteFormatter` is display-only and `parseBytes` does not exist
 * (`docs/system-architecture.md` §4.2).
 */
enum class ResultPhase {
    /** The count-up runs. The composable owns the animation and reports back with an Intent. */
    Counting,

    /** The headline, the lifetime total and the suggestions are on screen. */
    Revealed,
}

@Immutable
data class CleanResultState(
    val summary: CleanupSummary,
    val phase: ResultPhase = ResultPhase.Counting,

    /**
     * The lifetime total, read from `CleanupLedger`. **Not accumulated here**: the ledger is fed at
     * the source by the use case that freed the bytes (§8, rule 1). The competitor accumulates its
     * lifetime counter by re-parsing the formatted display string this screen produced.
     */
    val lifetimeFreedBytes: Long = 0L,

    /** Features the user has not opened lately, minus the one that just ran. */
    val suggestions: ImmutableList<FeatureId> = persistentListOf(),
) : UiState {

    val feature: FeatureId get() = summary.feature

    val freedBytes: Long get() = summary.freedBytes

    val itemCount: Int get() = summary.itemCount

    val isRevealed: Boolean get() = phase == ResultPhase.Revealed

    /**
     * `DataCleared` is why this screen can render a run that freed nothing and still be correct —
     * the photo-privacy strip removes location data and frees zero bytes. A byte figure is shown
     * only when bytes are what the run was about.
     */
    val showsBytes: Boolean get() = when (summary.outcome) {
        CleanupOutcome.Cleaned -> true
        CleanupOutcome.NothingFound -> false
        CleanupOutcome.ThreatsRemoved -> false
        CleanupOutcome.DataCleared -> false
        CleanupOutcome.ItemsCleared -> summary.freedBytes > 0L
    }
}

sealed interface CleanResultIntent : UiIntent {
    /** The composable owns the count-up and says when it is done. */
    data object CountingAnimationFinished : CleanResultIntent
    data class SuggestionTapped(val feature: FeatureId) : CleanResultIntent
    data object DonePressed : CleanResultIntent
    data object BackPressed : CleanResultIntent
}

sealed interface CleanResultEffect : UiEffect {
    data class NavigateToFeature(val feature: FeatureId) : CleanResultEffect

    /** `popUpTo(Home) { inclusive = false }` — what `finish()` did (`LLM.md` §7.1). */
    data object NavigateHome : CleanResultEffect
    data object NavigateBack : CleanResultEffect
}
