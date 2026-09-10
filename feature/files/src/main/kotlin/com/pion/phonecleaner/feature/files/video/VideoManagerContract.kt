package com.pion.phonecleaner.feature.files.video

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
 * `video` (`docs/screens/14-file-tools-and-app-manager.md` §3). Replaces `IndavailActivity` (426 L)
 * and `Allinat`.
 *
 * **The permission belongs in this contract, not in a router.** The competitor gates the whole screen
 * from outside and never starts the Activity if the check fails — which is why it has no permission
 * state at all, and why Android 14 partial access can only ever be an all-or-nothing decision taken
 * before the screen exists. Modelling it here lets the screen say *"only the items you selected are
 * listed — you can add more"*, which is the shape the platform wants.
 */
@Immutable
data class VideoManagerState(
    override val phase: ToolPhase = ToolPhase.Idle,
    val files: SelectableFiles<ScannedFile> = selectableFiles(),
    val access: MediaAccess = MediaAccess.Unknown,

    /** The competitor has no sort; its query order is fixed. */
    val sort: MediaSort = MediaSort.NewestFirst,

    /** The cursor read hit its bound. Surfaced, never reported as success. */
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

    val showPartialAccessBanner: Boolean get() = access == MediaAccess.Partial

    val showPermissionState: Boolean get() = access == MediaAccess.Denied
}

sealed interface VideoManagerIntent : UiIntent {
    data object ScreenStarted : VideoManagerIntent, FileToolIntent.Rescan

    /** The load starts from HERE, never from `init` (MVI §3: work that needs a permission). */
    data class PermissionResolved(val access: MediaAccess) : VideoManagerIntent
    data object GrantMorePressed : VideoManagerIntent
    data class SortSelected(val sort: MediaSort) : VideoManagerIntent
    data class RowToggled(override val id: String) : VideoManagerIntent, FileToolIntent.ToggleItem
    data object SelectAllToggled : VideoManagerIntent, FileToolIntent.ToggleSelectAll
    data class RowOpened(val id: String) : VideoManagerIntent
    data object DeletePressed : VideoManagerIntent, FileToolIntent.DeleteSelected
    data object DeleteConfirmed : VideoManagerIntent
    data object DeleteDismissed : VideoManagerIntent
    data object CompletionAnimationFinished : VideoManagerIntent
    data class DeleteConsentResult(val granted: Boolean) : VideoManagerIntent
    data object BackPressed : VideoManagerIntent
}

sealed interface VideoManagerEffect : UiEffect {
    data object RequestMediaPermission : VideoManagerEffect
    data class RequestDeleteConsent(val token: PendingIntentToken) : VideoManagerEffect
    data class OpenFile(val uri: String, val mimeType: String?) : VideoManagerEffect

    data class NavigateToCleanResult(val summary: CleanupSummary) :
        VideoManagerEffect, FileToolEffect.CleanFinished {
        override val bytesFreed: Long get() = summary.freedBytes
    }

    data object NavigateBack : VideoManagerEffect
}
