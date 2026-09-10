package com.pion.phonecleaner.feature.files.audio

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
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.feature.files.component.MediaAccess
import com.pion.phonecleaner.feature.files.component.MediaSort
import com.pion.phonecleaner.feature.files.component.selectableFiles

/**
 * `audio` (`docs/screens/14-file-tools-and-app-manager.md` §4). Replaces `ScreatisActivity` (433 L)
 * and `Inviosine`. **Structurally identical to `video`**, and its own contract file all the same: a
 * shared sealed hierarchy would make every reducer's `when` non-exhaustive for no gain (§0.3).
 *
 * Two differences, both stated rather than left to a silent `else`:
 *
 * 1. **[MediaAccess.Partial] is unreachable here.** `READ_MEDIA_AUDIO` has no user-selected variant,
 *    so there is no partial-grant banner and [showPermissionState] is the only permission surface.
 * 2. **The watchdog is visible.** The competitor's audio engine arms a 10 s watchdog that flips a
 *    `volatile` flag, stops the cursor loop mid-way and **reports success**. [scanTruncated] is that
 *    same bound, surfaced.
 */
@Immutable
data class AudioManagerState(
    override val phase: ToolPhase = ToolPhase.Idle,
    val files: SelectableFiles<ScannedFile> = selectableFiles(),
    val access: MediaAccess = MediaAccess.Unknown,
    val sort: MediaSort = MediaSort.NewestFirst,

    /** The cursor read hit its bound and published what it had. Never reported as success. */
    val scanTruncated: Boolean = false,
    val failedCount: Int = 0,
    val confirm: ConfirmSpec? = null,
    /** Mode stated by the confirmation; retained through the system consent round trip. */
    val trashEligible: Boolean = false,
    override val error: AppError? = null,
) : FileToolState {

    val selectedCount: Int get() = files.selectedCount

    val selectedBytes: Long get() = files.items.sumOf {
        if (it.id in files.selectedIds) it.sizeBytes else 0L
    }

    val canDelete: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0

    val showEmptyState: Boolean
        get() = phase == ToolPhase.Ready && files.items.isEmpty() && access.canLoad

    val showPermissionState: Boolean get() = access == MediaAccess.Denied
}

sealed interface AudioManagerIntent : UiIntent {
    data object ScreenStarted : AudioManagerIntent, FileToolIntent.Rescan
    data class PermissionResolved(val access: MediaAccess) : AudioManagerIntent
    data object GrantPressed : AudioManagerIntent
    data class SortSelected(val sort: MediaSort) : AudioManagerIntent
    data class RowToggled(override val id: String) : AudioManagerIntent, FileToolIntent.ToggleItem
    data object SelectAllToggled : AudioManagerIntent, FileToolIntent.ToggleSelectAll
    data class RowOpened(val id: String) : AudioManagerIntent
    data object DeletePressed : AudioManagerIntent, FileToolIntent.DeleteSelected
    data object DeleteConfirmed : AudioManagerIntent
    data object DeleteDismissed : AudioManagerIntent
    data object CompletionAnimationFinished : AudioManagerIntent
    data class DeleteConsentResult(val granted: Boolean) : AudioManagerIntent
    data object BackPressed : AudioManagerIntent
}

sealed interface AudioManagerEffect : UiEffect {
    data object RequestMediaPermission : AudioManagerEffect
    data class RequestDeleteConsent(val token: PendingIntentToken) : AudioManagerEffect
    data class OpenFile(val uri: String, val mimeType: String?) : AudioManagerEffect

    data class NavigateToCleanResult(val summary: CleanupSummary) :
        AudioManagerEffect, FileToolEffect.CleanFinished {
        override val bytesFreed: Long get() = summary.freedBytes
    }

    data object NavigateBack : AudioManagerEffect
}
