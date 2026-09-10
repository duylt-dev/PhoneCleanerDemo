package com.pion.phonecleaner.feature.files.duplicates

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.FileToolEffect
import com.pion.phonecleaner.core.mvi.FileToolIntent
import com.pion.phonecleaner.core.mvi.FileToolState
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.file.DuplicateGroup
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.file.ScannedFile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/**
 * `duplicates` (`docs/screens/14-file-tools-and-app-manager.md` §2). Replaces `MassutoActivity`
 * (567 L) and `Oversste` (766 L). The only screen in the module with **groups**.
 *
 * The shared machine is [FileToolState] / [FileToolIntent] / [FileToolEffect] in `:core:mvi`; each
 * screen still declares its own contract, because a shared sealed hierarchy would make every
 * reducer's `when` non-exhaustive for no gain (§0.3).
 *
 * It does **not** carry a `SelectableFiles`: that type holds one flat list, and this screen's rows
 * live inside [DuplicateGroup]s. The rule it does keep is the one that matters — the selection is a
 * `Set` of ids on the state, never an `isSelected` field on the row. The competitor writes
 * `isLatest` / `isSelected` onto its row model inside the hashing loop and then calls
 * `notifyDataSetChanged()` on every tap (§0.3).
 */
@Immutable
data class DuplicatesState(
    override val phase: ToolPhase = ToolPhase.Idle,
    val groups: ImmutableList<DuplicateGroup> = persistentListOf(),
    val selectedIds: ImmutableSet<String> = persistentSetOf(),

    /**
     * The shared volumes are readable. `null` = not asked yet, which is NOT the same as denied: on
     * the first composition the gate has not run, and drawing the denial panel there would flash a
     * permission screen at a user who already granted (`LLM.md` §7.4).
     *
     * Denial is a **state with a reason and a button**, never `finish()` — the competitor's answer
     * to the same problem (`TaribrActivity.java:151`).
     */
    val storageGranted: Boolean? = null,

    /**
     * Rows collected so far, before any byte is hashed. With all-files access the corpus is a walk
     * of every volume, which takes long enough that a screen showing only "compared 0 of 0" would
     * look hung.
     */
    val collected: Int = 0,

    /**
     * Which kind the list is narrowed to; `null` = all. The corpus now holds every extension on the
     * device, so a filter is what keeps a 900-row result usable.
     *
     * It is `FileKind` itself rather than a new screen-local enum: a finer split — Document versus
     * Archive — is asserted by no source and is not invented here (`FileKind` KDoc).
     */
    val filter: FileKind? = null,

    /** Live progress from the finder. The competitor shows none. */
    val hashed: Int = 0,
    val candidates: Int = 0,

    /**
     * The hashing budget ran out and what completed was published anyway. The competitor's
     * `withTimeoutOrNull(4000)` truncates and reports success (§2.2).
     */
    val scanTruncated: Boolean = false,

    /** Paths the last delete could not remove. Reported, never swallowed. */
    val failedCount: Int = 0,

    /** A dialog is STATE, never an Effect (`LLM.md` §7.4). */
    val confirm: ConfirmSpec? = null,
    /** Mode stated by the confirmation; retained through the system consent round trip. */
    val trashEligible: Boolean = false,

    /** The "View" sheet holds an **id**, not the object; `null` = closed (§2.1). */
    val previewingId: String? = null,
    override val error: AppError? = null,
) : FileToolState {

    val selectedCount: Int get() = selectedIds.size

    /**
     * What the list renders. The filter narrows the VIEW and never the selection: a user who filters
     * to Images after the pre-selection landed still deletes the videos they had selected, and a
     * filter that silently dropped them would delete less than the button said.
     */
    val visibleGroups: ImmutableList<DuplicateGroup>
        get() = filter?.let { kind -> groups.filter { it.kind == kind }.toImmutableList() } ?: groups

    /** The kinds actually present, so the chip row offers no filter that would empty the list. */
    val availableKinds: ImmutableList<FileKind>
        get() = groups.map(DuplicateGroup::kind).distinct().toImmutableList()

    /** Denial is a screen, so nothing else on it may draw while it is up. */
    val showPermissionPanel: Boolean get() = storageGranted == false

    val selectedBytes: Long
        get() = groups.sumOf { group ->
            group.files.sumOf { if (it.id in selectedIds) it.sizeBytes else 0L }
        }

    /** What keeping exactly one copy of every group would recover. */
    val reclaimableBytes: Long get() = groups.sumOf { it.reclaimableBytes }

    val canDelete: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0

    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && visibleGroups.isEmpty()

    val isBusy: Boolean get() = phase == ToolPhase.Scanning || phase == ToolPhase.Deleting

    val previewing: ScannedFile?
        get() = previewingId?.let { id ->
            groups.firstNotNullOfOrNull { group -> group.files.firstOrNull { it.id == id } }
        }
}

sealed interface DuplicatesIntent : UiIntent {
    /**
     * Raised on every `ON_START` with the gate's own answer, so a return from the Settings page
     * re-enters the same reducer. It replaces a bare `ScreenStarted`: the scan starts from
     * `granted = true` and from nothing else, which is what stops the screen scanning its own
     * sandbox and reporting "no duplicates" (`:core:ui/permission/StorageAccessGate.kt`).
     */
    data class StorageAccessResolved(val granted: Boolean) :
        DuplicatesIntent, FileToolIntent.Rescan

    /** The button on the denial panel. The Route owns which of the two grant shapes it launches. */
    data object GrantStoragePressed : DuplicatesIntent

    data class FilterSelected(val kind: FileKind?) : DuplicatesIntent

    /** The retry on the error card. A rescan the user asked for, not one an `ON_START` implied. */
    data object RetryPressed : DuplicatesIntent
    data class RowToggled(override val id: String) : DuplicatesIntent, FileToolIntent.ToggleItem
    data object DeselectAllPressed : DuplicatesIntent

    /** The pre-selection is the product; there must be a way back to it (§2.4). */
    data object SelectAllOlderPressed : DuplicatesIntent
    data class RowTapped(val id: String) : DuplicatesIntent
    data object PreviewDismissed : DuplicatesIntent
    data object PreviewConfirmed : DuplicatesIntent
    data object DeletePressed : DuplicatesIntent, FileToolIntent.DeleteSelected
    data object DeleteConfirmed : DuplicatesIntent
    data object DeleteDismissed : DuplicatesIntent
    data object CompletionAnimationFinished : DuplicatesIntent
    data class DeleteConsentResult(val granted: Boolean) : DuplicatesIntent
    data object BackPressed : DuplicatesIntent
}

sealed interface DuplicatesEffect : UiEffect {
    /** Only an Activity can launch either grant shape, so this is an Effect, not a state flag. */
    data object RequestStorageAccess : DuplicatesEffect

    data class OpenExternally(val uri: String, val mimeType: String?) : DuplicatesEffect
    data class RequestDeleteConsent(val token: PendingIntentToken) : DuplicatesEffect

    data class NavigateToCleanResult(val summary: CleanupSummary) :
        DuplicatesEffect, FileToolEffect.CleanFinished {
        override val bytesFreed: Long get() = summary.freedBytes
    }

    data object NavigateBack : DuplicatesEffect
}
