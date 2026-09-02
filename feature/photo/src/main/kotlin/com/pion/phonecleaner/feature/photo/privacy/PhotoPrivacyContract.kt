package com.pion.phonecleaner.feature.photo.privacy

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.photo.StripStep
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/**
 * `privacy` — `docs/screens/13-photo-and-media.md` §5.1. Replaces `DecoamazeActivity` (436 L).
 *
 * **There is no `viewType` and no header row in [selectedIds].** The competitor's model carries
 * `viewType: 0|1` and its adapter returns headers inside the selected list, which then reach the
 * strip engine with `path == ""`. Here a header is a `LazyVerticalGrid` span, not a model row, so it
 * cannot be selected.
 */
data class PhotoPrivacyState(
    val phase: ToolPhase = ToolPhase.Idle,
    /** Month buckets of geotagged photos, newest first — `PhotoGrouping.byMonth`. */
    val groups: ImmutableList<PhotoGroup> = persistentListOf(),
    val selectedIds: ImmutableSet<PhotoId> = persistentSetOf(),
    val scanned: Int = 0,
    val toScan: Int = 0,
    val isClearConfirmVisible: Boolean = false,
    val isStopConfirmVisible: Boolean = false,
    /** `null` ⇒ not running. */
    val strip: StripProgress? = null,
    val error: AppError? = null,
) : UiState {
    val selectedCount: Int get() = selectedIds.size
    val selectedBytes: Long
        get() = groups.sumOf { g -> g.photos.sumOf { if (it.id in selectedIds) it.sizeBytes else 0L } }
    val canClear: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0 && strip == null
    val showEmptyState: Boolean get() = phase == ToolPhase.Ready && groups.isEmpty()
    val isBusy: Boolean get() = phase == ToolPhase.Scanning || strip != null
}

/**
 * `failedCount` exists because the competitor's engine returns `true` unconditionally, which is what
 * makes its own failure toast unreachable (§5.5).
 */
data class StripProgress(val done: Int, val total: Int, val failedCount: Int) {
    val isFinished: Boolean get() = done + failedCount >= total

    fun fold(step: StripStep): StripProgress = copy(
        done = if (step.failed) done else done + 1,
        // The engine knows the real total: ids that no longer resolve never produce a step.
        total = step.total,
        failedCount = if (step.failed) failedCount + 1 else failedCount,
    )
}

sealed interface PhotoPrivacyIntent : UiIntent {
    data object ScreenStarted : PhotoPrivacyIntent
    data class PhotoToggled(val id: PhotoId) : PhotoPrivacyIntent
    data class MonthToggled(val key: String) : PhotoPrivacyIntent

    /**
     * The shared `:core:ui` `SelectionBar` carries a select-all affordance, so the screen owes an
     * intent for it. §5.1 lists only the month toggle; this is the same set arithmetic over every
     * group rather than one.
     */
    data object SelectAllToggled : PhotoPrivacyIntent
    data object ClearPressed : PhotoPrivacyIntent
    data object ClearConfirmed : PhotoPrivacyIntent
    data object ClearDismissed : PhotoPrivacyIntent
    data object CompletionAnimationFinished : PhotoPrivacyIntent
    data object BackPressed : PhotoPrivacyIntent

    /**
     * §5.2 requires "BackPressed while SCANNING or stripping raises a confirm and cancels the job".
     * §5.1's intent list stops at `BackPressed`, so these two are added here — the confirm cannot be
     * answered otherwise, and the junk cluster spells the same pair `StopConfirmed`/`StopDismissed`.
     */
    data object StopConfirmed : PhotoPrivacyIntent
    data object StopDismissed : PhotoPrivacyIntent
}

/**
 * UNKNOWN — §5.1 also declares `ShowMessage(val text: UiText)`; no `UiText` type exists anywhere in
 * this repository (see the same note on `AlbumDetailEffect`). The failed rows are carried on
 * [StripProgress.failedCount] and rendered by the screen instead.
 */
sealed interface PhotoPrivacyEffect : UiEffect {
    data class NavigateToCleanResult(val summary: CleanupSummary) : PhotoPrivacyEffect
    data object NavigateBack : PhotoPrivacyEffect
}
