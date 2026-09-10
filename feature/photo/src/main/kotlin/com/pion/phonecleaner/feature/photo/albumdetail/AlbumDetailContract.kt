package com.pion.phonecleaner.feature.photo.albumdetail

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `albumdetail` — `docs/screens/13-photo-and-media.md` §7.1. Replaces `MomringActivity` (439 L) and
 * the third static hand-off.
 *
 * DEVIATION, stated: §7.1 puts a `ConfirmSpec?` on the state. `ConfirmSpec` carries `@StringRes`
 * ids, which would put this module's generated `R` on the ViewModel and into its JVM unit test. The
 * junk cluster already set the precedent the other way (`JunkScanState.isStopConfirmVisible`), so
 * the flag is here and the copy is resolved by the composable.
 */
data class AlbumDetailState(
    /** The route argument, read once from `SavedStateHandle`. */
    val folderName: String = "",
    val phase: ToolPhase = ToolPhase.Idle,
    val photos: ImmutableList<Photo> = persistentListOf(),

    /** Selection is a set of ids on State — never an `isSelected` field on `Photo` (`LLM.md` §8). */
    val selectedIds: ImmutableSet<PhotoId> = persistentSetOf(),
    val isDeleteConfirmVisible: Boolean = false,
    val trashEligible: Boolean = false,
    val pendingConsentUris: ImmutableSet<String> = persistentSetOf(),
    val consentDeclined: Boolean = false,
    val failedCount: Int = 0,
    val error: AppError? = null,
) : UiState {
    val selectedCount: Int get() = selectedIds.size
    val selectedBytes: Long get() = photos.sumOf { if (it.id in selectedIds) it.sizeBytes else 0L }
    val canDelete: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0
    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && photos.isEmpty()
    val isAllSelected: Boolean get() = photos.isNotEmpty() && selectedIds.size == photos.size
    val isAwaitingConsent: Boolean get() = pendingConsentUris.isNotEmpty()
}

sealed interface AlbumDetailIntent : UiIntent {
    data object ScreenStarted : AlbumDetailIntent
    data class PhotoToggled(val id: PhotoId) : AlbumDetailIntent
    data object SelectAllToggled : AlbumDetailIntent
    data object DeletePressed : AlbumDetailIntent
    data object DeleteConfirmed : AlbumDetailIntent
    data object DeleteDismissed : AlbumDetailIntent
    data class DeleteConsentResult(val granted: Boolean) : AlbumDetailIntent
    data object BackPressed : AlbumDetailIntent
}

sealed interface AlbumDetailEffect : UiEffect {
    data class RequestDeleteConsent(val token: PendingIntentToken) : AlbumDetailEffect
    data class NavigateToCleanResult(val summary: CleanupSummary) : AlbumDetailEffect
    data object NavigateBack : AlbumDetailEffect
}
