package com.pion.phonecleaner.feature.photo.blurry

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
 * `blurry` — the blurry-photo grid.
 *
 * **This screen has no competitor counterpart.** `docs/reverse-engineering/13-photo-and-media.md:544`
 * records that no sharpness, resolution or size heuristic exists anywhere in the decompiled APK, so
 * unlike every other contract in this cluster there is no `java/…` line to cite and no observed
 * defect to avoid. The shape below is `SimilarPhotosState`'s, because the two screens do the same
 * job — scan, group, select, delete, with a pager over the result — and a second shape for the same
 * job would be two shapes to keep correct.
 *
 * The two places it deliberately differs from `similar` are both consequences of the owner's
 * decision that **every row arrives pre-selected**:
 *
 *  - There is no `GroupCleanupPressed`. "Keep one" is the similar screen's whole affordance and it
 *    has no meaning here: a blur tier is not a set of rivals, so there is no member to keep.
 *  - [isPreselected] exists, and the screen says so above the grid. A user who did not tick anything
 *    is about to delete everything the scan found; a screen that pre-ticks and stays quiet about it
 *    is the failure mode this whole build's wording rule exists to prevent.
 */
data class BlurryPhotosState(
    val phase: ToolPhase = ToolPhase.Idle,
    /** One group per `BlurTier` that found members, in tier order. Never an empty group. */
    val groups: ImmutableList<PhotoGroup> = persistentListOf(),
    val selectedIds: ImmutableSet<PhotoId> = persistentSetOf(),
    val scored: Int = 0,
    val toScore: Int = 0,
    /**
     * Photos the detector could not read. `BlurDetector.score` returns `Double?` and `null` is
     * excluded rather than treated as a score — zero is the *blurriest possible* reading, so scoring
     * an unreadable file zero would pre-tick every one of them for deletion.
     */
    val skipped: Int = 0,
    val isDeleteConfirmVisible: Boolean = false,
    /**
     * The `contentUri`s the system is currently asking the user about. `DeleteOutcome.PendingConsent`
     * is the **normal** API 30+ path, not an error, and this is the state that renders it (§0.1).
     */
    val pendingConsentUris: ImmutableSet<String> = persistentSetOf(),
    val consentDeclined: Boolean = false,
    val failedCount: Int = 0,
    val error: AppError? = null,
) : UiState {
    val totalBytes: Long get() = groups.sumOf { g -> g.photos.sumOf { it.sizeBytes } }
    val selectedBytes: Long
        get() = groups.sumOf { g -> g.photos.sumOf { if (it.id in selectedIds) it.sizeBytes else 0L } }
    val selectedCount: Int get() = selectedIds.size
    val canDelete: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0
    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && groups.isEmpty()
    val isBusy: Boolean get() = phase == ToolPhase.Scanning || phase == ToolPhase.Deleting

    /**
     * Whether the selection is still exactly what the scan pre-ticked — every row, untouched.
     *
     * This is what gates the notice above the grid. It goes false the moment the user unticks
     * anything, because from then on the selection is theirs and telling them it was made for them
     * would be false.
     */
    val isPreselected: Boolean
        get() = phase == ToolPhase.Ready &&
            groups.isNotEmpty() &&
            selectedCount == groups.sumOf { it.photos.size }
}

sealed interface BlurryPhotosIntent : UiIntent {
    data object ScreenStarted : BlurryPhotosIntent
    data class PhotoToggled(val id: PhotoId) : BlurryPhotosIntent

    /** One tier's header checkbox: select all of it, or clear all of it. */
    data class TierToggled(val groupKey: String) : BlurryPhotosIntent
    data object SelectAllToggled : BlurryPhotosIntent
    data object DeletePressed : BlurryPhotosIntent
    data object DeleteConfirmed : BlurryPhotosIntent
    data object DeleteDismissed : BlurryPhotosIntent
    data object CompletionAnimationFinished : BlurryPhotosIntent

    /**
     * Carries the id rather than `(groupKey, index)` for the reason `SimilarPhotosIntent.PhotoOpened`
     * does: a per-cell `(key, index)` lambda can only be built inside `items {}`, which `LLM.md` §8
     * bans. The reducer does the lookup and the **Effect** carries the pair.
     */
    data class PhotoOpened(val id: PhotoId) : BlurryPhotosIntent

    data class DeleteConsentResult(val granted: Boolean) : BlurryPhotosIntent
    data object BackPressed : BlurryPhotosIntent
}

sealed interface BlurryPhotosEffect : UiEffect {
    data class OpenPreview(val groupKey: String, val startIndex: Int) : BlurryPhotosEffect
    data class RequestDeleteConsent(val token: PendingIntentToken) : BlurryPhotosEffect
    data class NavigateToCleanResult(val summary: CleanupSummary) : BlurryPhotosEffect
    data object NavigateBack : BlurryPhotosEffect
}
