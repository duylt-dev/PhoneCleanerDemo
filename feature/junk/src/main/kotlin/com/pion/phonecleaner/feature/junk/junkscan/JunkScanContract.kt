package com.pion.phonecleaner.feature.junk.junkscan

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.junk.JunkCategoryId
import com.pion.phonecleaner.domain.model.junk.JunkScanMode

/**
 * The scan screen's contract (`docs/screens/12-junk-cleaning.md` §3.1).
 *
 * `JunkScanPhase`, never a bare `ScanPhase`: `docs/system-architecture.md` §3D retires that name
 * outright rather than awarding it, because four types claimed it and whichever won, the others
 * would be one careless import from compiling against the wrong one.
 *
 * It folds eight `TaribrActivity` fields and the whole of `wc.e` (513 L of `Handler` +
 * `ExecutorService` + batch downsampling built to feed one `TextView`). `startScanTime`,
 * `progressHelper` and `fromCleanComplete` are deleted outright (§3.1's folding table).
 */
enum class JunkScanPhase {
    CheckingPermission,
    PermissionRequired,
    Scanning,
    Finished,
    Failed,
}

/** `Candidate.total` when the pass genuinely cannot know it — the `.apk` walk (§1.2 point 2). */
const val CANDIDATE_COUNT_UNKNOWN: Int = -1

/** The three passes. `progress` divides by this, and `JunkScanner` runs exactly this many. */
private const val PASS_COUNT: Int = 3

data class JunkScanState(
    /** The route argument. See `JunkScanMode` — Express has no screen design of its own. */
    val mode: JunkScanMode = JunkScanMode.Review,
    val phase: JunkScanPhase = JunkScanPhase.CheckingPermission,
    val currentCategory: JunkCategoryId? = null,
    /** The file-name ticker. Changes on nearly every emission, so it is drawn by its own leaf. */
    val currentPath: String = "",
    val scannedCount: Int = 0,
    val candidateCount: Int = CANDIDATE_COUNT_UNKNOWN,
    val foundBytes: Long = 0L,
    /** 0..3 — the only honest progress source. */
    val passesFinished: Int = 0,
    val isStopConfirmVisible: Boolean = false,
    val completionAnimationFinished: Boolean = false,
    val error: AppError? = null,
) : UiState {

    val isScanning: Boolean get() = phase == JunkScanPhase.Scanning

    /**
     * `0f..1f`, or `null` for "indeterminate".
     *
     * Two thirds of the bar are the two determinate passes; the `.apk` walk owns the last third and
     * is reported indeterminate because it genuinely is. The competitor's bar has its target pinned
     * at 0 by integer division and moves on a timer, so a 40-second scan and a 2-second scan look
     * identical (`wc/e.java:173`, Delta S1).
     */
    val progress: Float?
        get() = when {
            phase == JunkScanPhase.Finished -> 1f
            phase != JunkScanPhase.Scanning -> null
            currentCategory == JunkCategoryId.ApkFiles -> null
            candidateCount <= 0 -> passesFinished / PASS_COUNT.toFloat()
            else -> (passesFinished + scannedCount.toFloat() / candidateCount) / PASS_COUNT
        }

    /**
     * Both conditions, computed. The competitor spells the same gate as a recursive Lottie replay
     * plus a 1 500 ms wall clock, with a synchronous callback on load failure
     * (`TaribrActivity.java:325-332`, Delta S2).
     */
    val canLeave: Boolean get() = phase == JunkScanPhase.Finished && completionAnimationFinished

    val isBusy: Boolean get() = isScanning
}

sealed interface JunkScanIntent : UiIntent {
    /** Reported by the composable, on every `ON_START`. The reducer is idempotent (MVI §4). */
    data class PermissionsResolved(val granted: Boolean) : JunkScanIntent
    data object GrantStorageTapped : JunkScanIntent
    data object CompletionAnimationFinished : JunkScanIntent
    data object BackPressed : JunkScanIntent
    data object StopConfirmed : JunkScanIntent
    data object StopDismissed : JunkScanIntent
    data object RetryTapped : JunkScanIntent
}

sealed interface JunkScanEffect : UiEffect {
    /** The payload is in `JunkSessionStore`; this route carries nothing (§8.1). */
    data object NavigateToReview : JunkScanEffect
    data object NavigateBack : JunkScanEffect
    data object RequestStoragePermission : JunkScanEffect

    /** Carries the error. Reading `state.error` in the collector reads the pre-failure value (MVI §4). */
    data class ShowMessage(val error: AppError) : JunkScanEffect
}
