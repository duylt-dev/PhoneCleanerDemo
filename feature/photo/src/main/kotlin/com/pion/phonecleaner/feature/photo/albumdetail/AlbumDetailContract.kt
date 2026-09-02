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

    /**
     * The rows the system consent dialog is deciding about, by `Photo.contentUri` — which is exactly
     * what `DeleteOutcome` hands back, so the reducer prunes without ever parsing a URI.
     *
     * `DeleteOutcome.PendingConsent` is a **state the UI renders**, not an error: on API 30+
     * `MediaStore.createDeleteRequest` raising a system dialog is the ordinary path
     * (`docs/system-architecture.md` §8.4).
     */
    val pendingConsentUris: ImmutableSet<String> = persistentSetOf(),

    /** The arm the competitor does not have: a cancelled consent dialog says so (§7.5). */
    val consentDeclined: Boolean = false,

    /** Rows the deleter reported it could not remove. Surfaced, never swallowed. */
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

    /** Reported by the Route's `ActivityResultLauncher`. Was `onActivityResult` in the competitor. */
    data class DeleteConsentResult(val granted: Boolean) : AlbumDetailIntent
    data object BackPressed : AlbumDetailIntent
}

/**
 * UNKNOWN — §7.1 also declares `ShowMessage(val text: UiText)`. **No `UiText` type exists** anywhere
 * in the repository (grepped `core/`, `domain/` and `feature/` for `class UiText` and
 * `interface UiText`; `:core:ui/error/ErrorMessages.kt` resolves an `AppError` to a string resource
 * instead). Rather than invent one, the two things §7.5 wants said — the failed rows and the
 * declined consent — are fields on [AlbumDetailState] that the screen renders. If a `UiText` lands
 * in `:core:ui`, this is the arm to add back.
 */
sealed interface AlbumDetailEffect : UiEffect {
    data class RequestDeleteConsent(val token: PendingIntentToken) : AlbumDetailEffect
    data class NavigateToCleanResult(val summary: CleanupSummary) : AlbumDetailEffect
    data object NavigateBack : AlbumDetailEffect
}
