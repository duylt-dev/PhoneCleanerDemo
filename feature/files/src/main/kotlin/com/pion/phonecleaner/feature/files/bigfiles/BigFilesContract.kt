package com.pion.phonecleaner.feature.files.bigfiles

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.FileToolEffect
import com.pion.phonecleaner.core.mvi.FileToolIntent
import com.pion.phonecleaner.core.mvi.FileToolState
import com.pion.phonecleaner.core.mvi.SelectableFiles
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.file.ScanCoverage
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.feature.files.component.selectableFiles

/**
 * `bigfiles` (`docs/screens/14-file-tools-and-app-manager.md` §1). Replaces `DownsivActivity` (496 L)
 * and `Irrnera` (422 L) — eight Activity fields and four ViewModel members.
 *
 * The shared machine lives in `:core:mvi` ([FileToolState], [FileToolIntent], [FileToolEffect],
 * [SelectableFiles], [ToolPhase]) and each screen still declares its own contract: a shared sealed
 * hierarchy would make every reducer's `when` non-exhaustive for no gain (§0.3). The interfaces are
 * what let `SelectionBar`, `FileRow` and the phase overlay take any of the six without an adapter.
 */
@Immutable
data class BigFilesState(
    override val phase: ToolPhase = ToolPhase.Idle,
    val files: SelectableFiles<ScannedFile> = selectableFiles(),

    /** Live progress. The competitor shows none, and fills the gap with a 4 000 ms animation. */
    val scannedCount: Int = 0,

    /** Which surfaces the current grant reached. Mandatory for the default branch (§8.4). */
    val coverage: ScanCoverage = ScanCoverage.Unknown,

    /** The scan hit its time bound and published what it had, rather than reporting success. */
    val scanTruncated: Boolean = false,

    /** Paths the last delete could not remove. Reported, never swallowed. */
    val failedCount: Int = 0,

    /** A dialog is STATE, never an Effect (`LLM.md` §7.4). */
    val confirm: ConfirmSpec? = null,
    override val error: AppError? = null,
) : FileToolState {

    val selectedCount: Int get() = files.selectedCount

    val selectedBytes: Long get() = files.items.sumOf {
        if (it.id in files.selectedIds) it.sizeBytes else 0L
    }

    val canDelete: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0

    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && files.items.isEmpty()

    val isBusy: Boolean get() = phase == ToolPhase.Scanning || phase == ToolPhase.Deleting
}

sealed interface BigFilesIntent : UiIntent {
    /** Raised on every `ON_START`, so returning from a grant re-enters the same reducer (§7.4). */
    data object ScreenStarted : BigFilesIntent, FileToolIntent.Rescan
    data class RowToggled(override val id: String) : BigFilesIntent, FileToolIntent.ToggleItem
    data object SelectAllToggled : BigFilesIntent, FileToolIntent.ToggleSelectAll
    data class RowOpened(val id: String) : BigFilesIntent
    data object DeletePressed : BigFilesIntent, FileToolIntent.DeleteSelected
    data object DeleteConfirmed : BigFilesIntent
    data object DeleteDismissed : BigFilesIntent
    data object CompletionAnimationFinished : BigFilesIntent

    /** Was `onActivityResult(1001)`: the `MediaStore` delete-consent round trip. */
    data class DeleteConsentResult(val granted: Boolean) : BigFilesIntent

    /** Opens a SAF tree picker. Never a `MANAGE_EXTERNAL_STORAGE` request (§0.2). */
    data object GrantMoreAccessPressed : BigFilesIntent
    data object BackPressed : BigFilesIntent
}

sealed interface BigFilesEffect : UiEffect {
    data class RequestDeleteConsent(val token: PendingIntentToken) : BigFilesEffect

    /** `ACTION_OPEN_DOCUMENT_TREE`. */
    data object RequestStorageTree : BigFilesEffect
    data class OpenFile(val uri: String, val mimeType: String?) : BigFilesEffect

    data class NavigateToCleanResult(val summary: CleanupSummary) :
        BigFilesEffect, FileToolEffect.CleanFinished {
        override val bytesFreed: Long get() = summary.freedBytes
    }

    data object NavigateBack : BigFilesEffect
}
