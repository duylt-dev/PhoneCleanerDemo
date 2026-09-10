package com.pion.phonecleaner.feature.files.videocompressrun

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.file.PendingIntentToken
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressOutcome
import com.pion.phonecleaner.domain.model.video.VideoCompressProgress
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet

/**
 * `videocompressrun` (`plans/260907-0142-video-compression/phase-07-run-screen.md`). D4's two-step
 * flow lives here: transcode first, offer the delete second, each separately confirmed.
 *
 * **`producedBytes` and `reclaimedBytes` are two different numbers, both on state (key insight 1).**
 * Until the delete step succeeds, nothing has been freed — the device holds MORE data than before,
 * not less. Reporting `producedBytes` as "saved" would be the same class of lie the wording rule
 * (`LLM.md` §1) forbids for a performance percentage: a figure that reads as more than it is.
 */
@Immutable
data class VideoCompressRunState(
    val videos: ImmutableList<VideoCandidate> = persistentListOf(),
    /** The route's argument. Never re-derived and never probed here (key insight 7). */
    val preset: VideoQualityPreset = VideoQualityPreset.Default,
    /** The route's argument. Never re-derived and never probed here (key insight 7). */
    val codec: VideoCodecOption = VideoCodecOption.Default,
    val isRunConfirmVisible: Boolean = false,
    val isStopConfirmVisible: Boolean = false,
    val isDeleteConfirmVisible: Boolean = false,
    /** Mode stated in the confirmation and passed unchanged to the delete use case. */
    val trashEligible: Boolean = false,
    /** `null` ⇒ the run has not started. */
    val run: VideoRunProgress? = null,
    /** How much smaller the new files are, in total. **Not** freed — the originals are still here. */
    val producedBytes: Long = 0L,
    /** Bytes actually reclaimed by deleting originals. Zero until the delete step succeeds. */
    val reclaimedBytes: Long = 0L,
    /** Set by the pre-flight check. Non-null ⇒ the run was refused and never started. */
    val spaceShortfall: Long? = null,
    /** Visible run budget, in seconds, so the user knows when this foreground job gives up. */
    val timeoutSeconds: Long = DEFAULT_RUN_TIMEOUT_SECONDS,
    /** Every id from the route resolved to nothing — process death, or the videos are gone. */
    val sessionLost: Boolean = false,
    val error: AppError? = null,
) : UiState {
    val isRunning: Boolean get() = run != null && !run.isFinished
    val isFinished: Boolean get() = run != null && run.isFinished
    /**
     * Deliberately NOT gated on [spaceShortfall]. Gating it there removed the only button on the
     * screen — `VideoCompressRunPrimaryAction` fell through to `else -> Unit` — so a user who freed
     * space had no way to ask again but to leave and come back. The shortfall message stays until the
     * next attempt clears it; pressing the button re-runs the check.
     */
    val canCompress: Boolean get() = videos.isNotEmpty() && run == null

    /** Only offered when there is something to delete AND something was produced. */
    /**
     * What pressing *remove originals* actually frees: the total size of the originals that produced
     * a smaller copy — NOT [producedBytes].
     *
     * The two differ and the difference is the whole point of D4's two-step flow. After a run the
     * device holds both files: a 22.9 MB original plus a 9.3 MB copy. [producedBytes] is 13.6 MB —
     * how much smaller the copy is. Deleting the original frees the original's 22.9 MB. Labelling the
     * delete button with 13.6 MB promises a figure the action does not produce, which is the same
     * class of error this class's KDoc forbids, only pointing the other way. Measured on device
     * 2026-09-07 (Samsung SM-A165F) with exactly those numbers.
     */
    val deletableBytes: Long
        get() = run?.succeededIds
            ?.let { ids -> videos.filter { it.id in ids }.sumOf { it.sizeBytes } }
            ?: 0L

    val canDeleteOriginals: Boolean get() = isFinished && producedBytes > 0L && reclaimedBytes == 0L

    /** The line that stops this feature reading as broken: the originals are still on the device. */
    val showOriginalsKeptNotice: Boolean get() = isFinished && reclaimedBytes == 0L

    companion object {
        const val DEFAULT_RUN_TIMEOUT_SECONDS: Long = 30 * 60
    }
}

/**
 * `CompressProgress.fold()` (`feature/photo/.../compressrun/CompressRunContract.kt`) with a third
 * outcome. Same invariants, restated because they are the ones that have already cost bugs:
 * - [isFinished] is **computed** from [done]+[skipped]+[failed] against [total], never a latch that
 *   only goes up.
 * - [total] closes onto what the engine actually reported when the flow ends — leaving it at the
 *   requested count parks the bar for ever.
 * - [savedBytes] accumulates what was **measured**, never a fraction of the selection.
 * - A [VideoCompressProgress.Working] arm never touches a counter. It only moves [currentPercent].
 *
 * **A video that could not be made smaller is not a failure and not a success**, and the user waited
 * minutes for it either way. Folding it into [done] would inflate the count; folding it into [failed]
 * would say something went wrong when nothing did — [skipped] is the third, first-class outcome.
 */
data class VideoRunProgress(
    val done: Int,
    val skipped: Int,
    val failed: Int,
    val total: Int,
    val savedBytes: Long,
    val currentIndex: Int,
    val currentId: String?,
    /** `null` ⇒ the engine has not produced a figure yet; the bar is indeterminate, not zero. */
    val currentPercent: Int?,
    /** Seconds since the run started, updated by the ViewModel's monotonic clock. */
    val elapsedSeconds: Long,
    /** Source ids the engine actually re-encoded. The delete step names only these (open question 2). */
    val succeededIds: ImmutableSet<String> = persistentSetOf(),
) {
    val settled: Int get() = done + skipped + failed
    val isFinished: Boolean get() = settled >= total
    val overallPercent: Int
        get() = if (total <= 0) {
            0
        } else {
            val currentShare = (currentPercent ?: 0).coerceIn(0, 100) / 100f
            val completedBeforeCurrent = (currentIndex - 1).coerceIn(settled, total)
            (((completedBeforeCurrent + currentShare) / total) * 100).toInt().coerceIn(0, 100)
        }
    val estimatedRemainingSeconds: Long?
        get() {
            val percent = overallPercent
            if (isFinished || elapsedSeconds <= 0L || percent <= 0) return null
            val estimatedTotalSeconds = (elapsedSeconds * 100f / percent).toLong()
            return (estimatedTotalSeconds - elapsedSeconds).coerceAtLeast(0L)
        }
    fun timeoutRemainingSeconds(timeoutSeconds: Long): Long =
        (timeoutSeconds - elapsedSeconds).coerceAtLeast(0L)

    companion object {
        /** The zeroed progress a run starts from, [total] being the requested count. */
        fun starting(total: Int): VideoRunProgress = VideoRunProgress(
            done = 0,
            skipped = 0,
            failed = 0,
            total = total,
            savedBytes = 0L,
            currentIndex = 0,
            currentId = null,
            currentPercent = null,
            elapsedSeconds = 0L,
        )
    }

    fun fold(progress: VideoCompressProgress, elapsedSeconds: Long = this.elapsedSeconds): VideoRunProgress = when (progress) {
        is VideoCompressProgress.Working -> copy(
            currentIndex = progress.index,
            currentId = progress.id,
            currentPercent = progress.percent,
            elapsedSeconds = elapsedSeconds,
        )

        is VideoCompressProgress.Finished -> {
            val step = progress.step
            copy(
                done = if (step.outcome == VideoCompressOutcome.Compressed) done + 1 else done,
                skipped = if (step.outcome == VideoCompressOutcome.NotSmaller) skipped + 1 else skipped,
                failed = if (step.outcome == VideoCompressOutcome.Failed) failed + 1 else failed,
                total = step.total,
                savedBytes = savedBytes + step.savedBytes,
                succeededIds = if (step.outcome == VideoCompressOutcome.Compressed) {
                    (succeededIds + step.id).toImmutableSet()
                } else {
                    succeededIds
                },
                currentPercent = null,
                elapsedSeconds = elapsedSeconds,
            )
        }
    }
}

sealed interface VideoCompressRunIntent : UiIntent {
    data object ScreenStarted : VideoCompressRunIntent
    data object CompressAllPressed : VideoCompressRunIntent
    data object CompressConfirmed : VideoCompressRunIntent
    data object CompressDismissed : VideoCompressRunIntent
    data object CompletionAnimationFinished : VideoCompressRunIntent
    data object DeleteOriginalsPressed : VideoCompressRunIntent
    data object DeleteOriginalsConfirmed : VideoCompressRunIntent
    data object DeleteOriginalsDismissed : VideoCompressRunIntent
    data class DeleteConsentResult(val granted: Boolean) : VideoCompressRunIntent
    data object BackPressed : VideoCompressRunIntent
    data object StopConfirmed : VideoCompressRunIntent

    /**
     * `docs/screens/13` §4.1 lists a stop confirm with no counterpart, and a confirm that cannot be
     * declined is a trap. `privacy` and photo's `compressrun` both spell this pair `Stop*`.
     */
    data object StopDismissed : VideoCompressRunIntent
}

/**
 * **Scalars, and no request object anywhere** (key insight 7, `LLM.md` §7.2). [PendingIntentToken] is
 * a token the ViewModel never calls a method on, not a payload — the one deliberate platform leak
 * `LLM.md` §12 blesses.
 */
sealed interface VideoCompressRunEffect : UiEffect {
    data class RequestDeleteConsent(val token: PendingIntentToken) : VideoCompressRunEffect
    data class NavigateToCleanResult(val summary: CleanupSummary) : VideoCompressRunEffect
    data object NavigateBack : VideoCompressRunEffect
}
