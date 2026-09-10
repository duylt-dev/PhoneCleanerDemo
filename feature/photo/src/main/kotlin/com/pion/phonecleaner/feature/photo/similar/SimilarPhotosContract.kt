package com.pion.phonecleaner.feature.photo.similar

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `similar` — `docs/screens/13-photo-and-media.md` §1.1. Replaces `LeauencActivity` (900 L) plus
 * `Paniclfar`, `vc.a/b/c/d`, `nd/a` and `od/b0`.
 *
 * Eight Activity fields and two `Observer`s fold into this one object. `checkPosition` is gone
 * outright: the preview writes the selection to `SimilarPhotoSessionStore` and this grid observes
 * it, so there is no index to carry.
 */
data class SimilarPhotosState(
    val phase: ToolPhase = ToolPhase.Idle,
    val groups: ImmutableList<PhotoGroup> = persistentListOf(),
    val selectedIds: ImmutableSet<PhotoId> = persistentSetOf(),
    val hashed: Int = 0,
    val toHash: Int = 0,
    /**
     * Photos the hasher could not read. `PerceptualHasher.hash` returns `Long?` and `null` is
     * excluded from grouping rather than treated as a hash — the competitor's `b0.h` returns `0L`
     * for an undecodable bitmap, so every unreadable file lands in one "similar" group (§1.4).
     */
    val skipped: Int = 0,
    val isDeleteConfirmVisible: Boolean = false,
    val trashEligible: Boolean = false,
    val pendingConsentUris: ImmutableSet<String> = persistentSetOf(),
    val consentDeclined: Boolean = false,
    val failedCount: Int = 0,
    val error: AppError? = null,
) : UiState {
    val totalBytes: Long get() = groups.sumOf { g -> g.photos.sumOf { it.sizeBytes } }
    val selectedBytes: Long get() = groups.sumOf { g -> g.photos.sumOf { if (it.id in selectedIds) it.sizeBytes else 0L } }
    val selectedCount: Int get() = selectedIds.size
    val canDelete: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0
    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && groups.isEmpty()
    val isBusy: Boolean get() = phase == ToolPhase.Scanning || phase == ToolPhase.Deleting
}

sealed interface SimilarPhotosIntent : UiIntent {
    data object ScreenStarted : SimilarPhotosIntent
    data class PhotoToggled(val id: PhotoId) : SimilarPhotosIntent
    data class GroupCleanupPressed(val groupKey: String) : SimilarPhotosIntent
    data object SelectAllToggled : SimilarPhotosIntent
    data object DeletePressed : SimilarPhotosIntent
    data object DeleteConfirmed : SimilarPhotosIntent
    data object DeleteDismissed : SimilarPhotosIntent
    data object CompletionAnimationFinished : SimilarPhotosIntent
    data class PhotoOpened(val id: PhotoId) : SimilarPhotosIntent
    data class DeleteConsentResult(val granted: Boolean) : SimilarPhotosIntent
    data object BackPressed : SimilarPhotosIntent
}

sealed interface SimilarPhotosEffect : UiEffect {
    data class OpenPreview(val groupKey: String, val startIndex: Int) : SimilarPhotosEffect
    data class RequestDeleteConsent(val token: PendingIntentToken) : SimilarPhotosEffect
    data class NavigateToCleanResult(val summary: CleanupSummary) : SimilarPhotosEffect
    data object NavigateBack : SimilarPhotosEffect
}
