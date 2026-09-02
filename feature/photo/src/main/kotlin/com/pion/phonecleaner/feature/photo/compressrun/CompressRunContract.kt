package com.pion.phonecleaner.feature.photo.compressrun

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.photo.CompressStep
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * `compressrun` — `docs/screens/13-photo-and-media.md` §4.1. Replaces `HerbrrenActivity` (411 L) and
 * the second of the cluster's three static hand-offs.
 *
 * **[page] and the compression index are two different fields.** In the competitor they are one
 * `currentPosition`, which is why its progress counter drives the pager and reads "(1/N)" while
 * photo 1 is already done (§4.1).
 */
data class CompressRunState(
    val photos: ImmutableList<Photo> = persistentListOf(),
    /** The preview pager position ONLY. It never moves because a photo finished. */
    val page: Int = 0,
    val isCompressConfirmVisible: Boolean = false,
    val isStopConfirmVisible: Boolean = false,
    /** `null` ⇒ the run has not started. */
    val run: CompressProgress? = null,
    /** Every id from the route resolved to nothing — process death, or the photos are gone (§4.2). */
    val sessionLost: Boolean = false,
    val error: AppError? = null,
) : UiState {
    val current: Photo? get() = photos.getOrNull(page)
    val isRunning: Boolean get() = run != null && !run.isFinished
    val isFinished: Boolean get() = run != null && run.isFinished
    val canCompress: Boolean get() = photos.isNotEmpty() && run == null
    val counterPosition: Int get() = page + 1
    val counterTotal: Int get() = photos.size
}

/**
 * **A latch that only goes up is not a state.** The competitor never resets its `compressing` flag;
 * [isFinished] is computed from counts that the engine emits, so it cannot be wrong (§4.5).
 *
 * [failedCount] is real. A photo the encoder could not handle is a `CompressStep(failed = true)`,
 * not a silent skip inside a `runCatching`.
 */
data class CompressProgress(
    val done: Int,
    val total: Int,
    val savedBytes: Long,
    val failedCount: Int,
    val currentId: PhotoId?,
) {
    val isFinished: Boolean get() = done + failedCount >= total

    /** `savedBytes` accumulates what was **measured**, never `0.6 × selectedBytes` (§4.5). */
    fun fold(step: CompressStep): CompressProgress = copy(
        done = if (step.failed) done else done + 1,
        // The engine knows the real total: an id that no longer resolves never produces a step.
        total = step.total,
        savedBytes = savedBytes + if (step.failed) 0L else step.savedBytes,
        failedCount = if (step.failed) failedCount + 1 else failedCount,
        currentId = step.id,
    )
}

sealed interface CompressRunIntent : UiIntent {
    data object ScreenStarted : CompressRunIntent
    data class PageChanged(val index: Int) : CompressRunIntent
    data object CompressAllPressed : CompressRunIntent
    data object CompressConfirmed : CompressRunIntent
    data object CompressDismissed : CompressRunIntent
    data object CompletionAnimationFinished : CompressRunIntent
    data object BackPressed : CompressRunIntent
    data object CancelRunConfirmed : CompressRunIntent

    /**
     * §4.1 lists `CancelRunConfirmed` without its counterpart, and a confirm that cannot be declined
     * is a trap. `privacy` spells the same pair `StopConfirmed` / `StopDismissed`.
     */
    data object CancelRunDismissed : CompressRunIntent
}

/**
 * UNKNOWN — §4.1 also declares `ShowMessage(val text: UiText)`; no `UiText` type exists anywhere in
 * this repository (see the same note on `PhotoCompressorEffect`). What it would have carried —
 * failed photos, and a lost selection — is on [CompressProgress.failedCount] and
 * [CompressRunState.sessionLost], both rendered by the screen.
 */
sealed interface CompressRunEffect : UiEffect {
    data class NavigateToCleanResult(val summary: CleanupSummary) : CompressRunEffect
    data object NavigateBack : CompressRunEffect
}
