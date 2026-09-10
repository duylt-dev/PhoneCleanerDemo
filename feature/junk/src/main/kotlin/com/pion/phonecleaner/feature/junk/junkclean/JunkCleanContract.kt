package com.pion.phonecleaner.feature.junk.junkclean

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState

/**
 * The clean screen's contract (`docs/screens/12-junk-cleaning.md` §5.1).
 *
 * Seven `MenaremovActivity` fields collapse into these. Three are deleted: the displayed remaining
 * value (the composable animates toward [JunkCleanState.remainingBytes] instead), the
 * `ValueAnimator` field, and `fromCleanComplete` — which that Activity reads into a field and never
 * uses again even there.
 */
enum class CleanPhase {
    Cleaning,
    Finished,
    Failed,

    /** Storage access was revoked between the review screen and this one (Delta C2). */
    PermissionLost,
}

data class JunkCleanState(
    /** What the review screen said was selected. A promise, never a report. */
    val promisedBytes: Long = 0L,

    /** What was **actually** deleted. */
    val freedBytes: Long = 0L,
    val processed: Int = 0,
    val total: Int = 0,
    val failedCount: Int = 0,

    /**
     * Advisory until the run ends, then factual — two lifetimes on purpose.
     *
     * `start()` seeds it from `permissions.isGranted(AppPermission.AllFiles)`, the same advisory read
     * every other delete confirm in this build uses, because the STOP dialog has to be captioned
     * before anything has moved. `CleanProgress.Finished` then overwrites it with
     * `CleanOutcome.recoverable`, which is `TrashRepository.isAvailable()` at the moment
     * `CleanJunkUseCase` actually moved each path. Only the second value reaches the result screen; if
     * the two disagree the user was told the more cautious thing first, which is the right direction
     * to be wrong in (plan `260908-0801-trash-bin`, Phase 07).
     */
    val recoverable: Boolean = false,
    val phase: CleanPhase = CleanPhase.Cleaning,
    val isStopConfirmVisible: Boolean = false,
    val finishAnimationEnded: Boolean = false,
    val error: AppError? = null,
) : UiState {

    /**
     * The number on screen, driven by real deletions.
     *
     * The competitor counts down `totalSize * (N - n) / N` — a linear ramp over the *promised* size,
     * which reaches zero when the last path was **attempted**, deleted or not
     * (`MenaremovActivity.java:428-433`, Delta C4).
     */
    val remainingBytes: Long get() = (promisedBytes - freedBytes).coerceAtLeast(0L)

    val isCleaning: Boolean get() = phase == CleanPhase.Cleaning
    val hadFailures: Boolean get() = failedCount > 0

    /** A determinate bar is possible here, because `processed / total` is real (§5.3). */
    val progress: Float? get() = if (total > 1) processed.toFloat() / total else null

    val canLeave: Boolean get() = phase == CleanPhase.Finished && finishAnimationEnded
}

sealed interface JunkCleanIntent : UiIntent {
    data object BackPressed : JunkCleanIntent
    data object StopConfirmed : JunkCleanIntent
    data object StopDismissed : JunkCleanIntent
    data object FinishAnimationEnded : JunkCleanIntent
    data object GrantStorageTapped : JunkCleanIntent
}

sealed interface JunkCleanEffect : UiEffect {
    /**
     * Carries its payload. MVI §4 — an effect must carry everything its handler needs.
     *
     * The competitor reads `cleanedSize` off an Activity field one statement before navigating, which
     * works only because the Activity is the same object.
     *
     * `:app`'s NavHost turns this into `CleanupSummary(feature = FeatureId.JunkClean, freedBytes,
     * itemCount, outcome)` for the one shared `CleanResult` route (§8.1).
     */
    data class NavigateToResult(
        val freedBytes: Long,
        val failedCount: Int,
        /** From `CleanOutcome.recoverable`, so `:app` picks `MovedToTrash` instead of guessing `Cleaned`. */
        val recoverable: Boolean,
    ) : JunkCleanEffect

    data object NavigateBack : JunkCleanEffect
    data object RequestStoragePermission : JunkCleanEffect
    data class ShowMessage(val error: AppError) : JunkCleanEffect
}
