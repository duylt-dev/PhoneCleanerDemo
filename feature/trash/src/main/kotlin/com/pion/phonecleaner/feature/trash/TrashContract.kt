package com.pion.phonecleaner.feature.trash

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.model.trash.TrashEntryType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

/**
 * `trash` (plan `260908-0801-trash-bin` phase 05). State + Intent + Effect only — see
 * `TrashViewModel.kt` for the reducer and `TrashReducers.kt` for the pure transitions.
 */
@Immutable
data class TrashState(
    val phase: TrashPhase = TrashPhase.Loading,
    /** Capped by `TrashEntryDao.observeTrashed()`'s own `LIMIT` at [TrashViewModel.MAX_ROWS]; see LLM.md §8 on unbounded lists. */
    val entries: ImmutableList<TrashEntry> = persistentListOf(),
    /** LLM.md §8: selection is a Set of ids on State, never an `isSelected` field on the model. */
    val selectedIds: ImmutableSet<String> = persistentSetOf(),
    /** The whole bin's byte total, from `ObserveTrashSummaryUseCase` — not the selection's. */
    val totalBytes: Long = 0L,
    /** Uncapped Room summary; an empty-bin confirmation must include rows beyond the visible 500. */
    val totalEntries: Int = 0,
    /** Distinguishes the initial loading frame from an already running reconciliation. */
    val isReconciling: Boolean = false,
    /**
     * Recomputed on every emission, from the injected clock — never stored on the entry. A countdown
     * frozen at load time reads "1 day left" on a screen the user left open overnight.
     */
    val now: Instant = Instant.DISTANT_PAST,
    /** LLM.md §7.4: a dialog is a nullable field on State so it survives a rotation. */
    val confirm: ConfirmSpec? = null,
    val pendingAction: TrashAction? = null,
    /** Snapshot of the selection the confirmation describes; later emissions cannot add targets. */
    val pendingIds: ImmutableSet<String> = persistentSetOf(),
    /**
     * False when `AppPermission.AllFiles` is missing. The list still renders — rows put there while
     * the grant was held are still restorable is NOT true without it, so the actions are disabled and
     * the card says why. Re-read on every resume (LLM.md §7.4).
     */
    val isTrashAvailable: Boolean = false,
    val selectedTab: TrashTab = TrashTab.Original,
    /**
     * Set only by a failed [TrashPhase.Loading]/reconcile pass — rendered inline (`ErrorCard`,
     * `BigFilesScreen`/`BlurryPhotosScreen`'s own shape) and read back by the `ScreenStarted` retry
     * gate. A restore/delete/empty failure never writes this field: it has a list to come back to and
     * nowhere to park a second, unrelated retry reason, so it goes out as `TrashEffect.ShowMessage`
     * only (MVI doc §4 — "a failure in state no composable draws" is the anti-pattern this avoids).
     */
    val error: AppError? = null,
) : UiState {
    val visibleEntries: ImmutableList<TrashEntry>
        get() = entries.filter { it.type == selectedTab.entryType }.toImmutableList()
    val isEmpty: Boolean get() = phase == TrashPhase.Ready && visibleEntries.isEmpty()
    val selectedCount: Int get() = selectedIds.size
    val selectedBytes: Long get() = entries.filter { it.id in selectedIds }.sumOf { it.sizeBytes }
    val canSelect: Boolean get() = phase == TrashPhase.Ready && confirm == null
    val canAct: Boolean get() = isTrashAvailable && selectedIds.isNotEmpty() && canSelect
    val canEmptyBin: Boolean get() = isTrashAvailable && totalEntries > 0 && canSelect
    val isAllSelected: Boolean get() = visibleEntries.isNotEmpty() && selectedIds.size == visibleEntries.size
}

enum class TrashPhase { Loading, Ready, Working }
enum class TrashAction { Restore, DeleteForever, EmptyAll }
enum class TrashTab(val entryType: TrashEntryType) { Original(TrashEntryType.Original), Zip(TrashEntryType.Zip) }

sealed interface TrashIntent : UiIntent {
    data object ScreenStarted : TrashIntent
    data object ScreenResumed : TrashIntent
    data class EntryToggled(val id: String) : TrashIntent
    data class TabSelected(val tab: TrashTab) : TrashIntent
    data object SelectAllToggled : TrashIntent
    data object RestorePressed : TrashIntent
    data object DeleteForeverPressed : TrashIntent
    data object EmptyBinPressed : TrashIntent
    data object ConfirmAccepted : TrashIntent
    data object ConfirmDismissed : TrashIntent
    data object AllowAccessPressed : TrashIntent
    data object BackPressed : TrashIntent
}

sealed interface TrashEffect : UiEffect {
    data object NavigateBack : TrashEffect

    /** Only an Activity can start the all-files Settings page; `:app` owns the launcher. */
    data object RequestAllFilesAccess : TrashEffect

    /** Carries the error. Reading `state.error` in the collector reads the pre-failure value (MVI §4). */
    data class ShowMessage(val error: AppError) : TrashEffect

    /**
     * A restore that renamed or skipped something has to say so; there is nowhere on the list to
     * draw it once the row is gone from `observeEntries()`. [failed] surfaces
     * `TrashRestoreOutcome.failedIds` through `R.plurals.trash_failed_count`, which otherwise has no
     * caller in this module — restore is the only outcome here that can fail a subset while the call
     * itself still reports `AppResult.Success`.
     */
    data class ShowRestored(val restored: Int, val renamed: Int, val failed: Int) : TrashEffect

    /** Partial permanent deletion is a result the user must see, even when the call succeeded. */
    data class ShowDeleteFailures(val failed: Int) : TrashEffect
}
