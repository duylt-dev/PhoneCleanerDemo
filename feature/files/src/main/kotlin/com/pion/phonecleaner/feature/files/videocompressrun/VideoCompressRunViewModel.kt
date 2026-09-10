package com.pion.phonecleaner.feature.files.videocompressrun

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.video.VideoSpaceCheck
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.PermissionRepository
import com.pion.phonecleaner.domain.repository.VideoCandidateRepository
import com.pion.phonecleaner.domain.usecase.CheckSpaceForCompressionUseCase
import com.pion.phonecleaner.domain.usecase.CompressVideosUseCase
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.EstimateVideoCompressionUseCase
import com.pion.phonecleaner.feature.files.component.cleanupSummaryFor
import kotlinx.coroutines.Job

/**
 * `videocompressrun` (`plans/260907-0142-video-compression/phase-07-run-screen.md`).
 *
 * ids/preset/codec are route scalars, never a bundled request object (key insight 7). Rows are
 * re-read from [VideoCandidateRepository]; an id that no longer resolves is dropped, an empty result
 * is [VideoCompressRunState.sessionLost]. `runJob`'s per-video work is a structural child of it, so
 * `onCleared` cancels the run too, and a cancelled run leaves no partial file.
 */
class VideoCompressRunViewModel(
    savedState: SavedStateHandle,
    private val compressVideos: CompressVideosUseCase,
    private val estimateCompression: EstimateVideoCompressionUseCase,
    private val checkSpace: CheckSpaceForCompressionUseCase,
    private val deleteFiles: DeleteFilesUseCase,
    private val videos: VideoCandidateRepository,
    private val analytics: AnalyticsRepository,
    private val permissions: PermissionRepository,
    log: AppLogger,
) : MviViewModel<VideoCompressRunState, VideoCompressRunIntent, VideoCompressRunEffect>(
    VideoCompressRunState(
        preset = savedState.readPreset(PRESET_ARG),
        codec = savedState.readCodec(CODEC_ARG),
    ),
    log,
) {
    private val requestedIds: List<String> = savedState.readVideoIds(VIDEO_IDS_ARG)
    private var runJob: Job? = null

    /** What the user asked to delete, so a consent round trip re-issues exactly that. */
    private var pendingDeleteIds: Set<String> = emptySet()

    override fun onIntent(intent: VideoCompressRunIntent) {
        when (intent) {
            VideoCompressRunIntent.ScreenStarted -> onScreenStarted()
            VideoCompressRunIntent.CompressAllPressed -> onCompressAllPressed()
            VideoCompressRunIntent.CompressConfirmed -> onCompressConfirmed()
            VideoCompressRunIntent.CompressDismissed -> setState { copy(isRunConfirmVisible = false) }
            // `run.isFinished` already drives the result panel; nothing else to reduce here.
            VideoCompressRunIntent.CompletionAnimationFinished -> Unit
            VideoCompressRunIntent.DeleteOriginalsPressed -> if (currentState.canDeleteOriginals) {
                setState {
                    copy(
                        isDeleteConfirmVisible = true,
                        trashEligible = permissions.isGranted(AppPermission.AllFiles),
                    )
                }
            }
            VideoCompressRunIntent.DeleteOriginalsConfirmed -> onDeleteOriginalsConfirmed()
            VideoCompressRunIntent.DeleteOriginalsDismissed ->
                setState { copy(isDeleteConfirmVisible = false) }
            is VideoCompressRunIntent.DeleteConsentResult -> onConsentResult(intent.granted)
            VideoCompressRunIntent.BackPressed -> onBackPressed()
            VideoCompressRunIntent.StopConfirmed -> onStopConfirmed()
            VideoCompressRunIntent.StopDismissed -> setState { copy(isStopConfirmVisible = false) }
        }
    }

    /** Retry raises this too: with rows loaded, clearing the error is the only thing left to do (§11 row 11). */
    private fun onScreenStarted() {
        val state = currentState
        if (state.videos.isNotEmpty() || state.run != null) {
            if (state.error != null) setState { copy(error = null) }
            return
        }
        analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.VideoCompressor))
        launchSafely(onError = { setState { withFailure(it) } }) {
            when (val result = videos.rowsFor(requestedIds)) {
                is AppResult.Failure -> setState { withFailure(result.error) }
                is AppResult.Success -> setState { withLoaded(result.value) }
            }
        }
    }

    /** A row with no usable estimate contributes nothing: the check must never refuse a run for it. */
    private fun onCompressAllPressed() {
        if (!currentState.canCompress) return
        setState { copy(spaceShortfall = null) } // A fresh attempt re-asks; a stale refusal must not describe it.

        launchSafely(onError = { setState { withFailure(it) } }) {
            val estimatedOutputs = currentState.videos.mapNotNull { candidate ->
                val estimate = estimateCompression(listOf(candidate), currentState.preset, currentState.codec)
                estimate.estimatedAfterBytes.takeIf { estimate.measuredCount > 0 }
            }
            when (val result = checkSpace(estimatedOutputs)) {
                is AppResult.Failure -> setState { withFailure(result.error) }
                is AppResult.Success -> when (val check = result.value) {
                    is VideoSpaceCheck.Short -> setState { copy(spaceShortfall = check.byBytes) }
                    VideoSpaceCheck.Sufficient -> setState { copy(isRunConfirmVisible = true) }
                }
            }
        }
    }

    private fun onCompressConfirmed() {
        val ids = currentState.videos.map { it.id }
        val preset = currentState.preset
        val codec = currentState.codec
        setState { copy(isRunConfirmVisible = false) }
        if (ids.isEmpty()) return
        runJob?.cancel()
        runJob = launchSafely(onError = { setState { withFailure(it) } }) {
            var progress = VideoRunProgress.starting(ids.size)
            setState { copy(run = progress, error = null) }
            compressVideos(ids, preset, codec).collect { step ->
                progress = progress.fold(step)
                setState { copy(run = progress) }
            }
            // Close `total` onto what actually settled — left at the requested count it would park
            // the bar for ever, the competitor's never-reset latch by another route.
            progress = progress.copy(total = progress.settled)
            setState { copy(run = progress, producedBytes = progress.savedBytes) }
        }
    }

    /** Back while running asks; the confirm body already promises that leaving cancels the job. */
    private fun onBackPressed() {
        if (currentState.isRunning) setState { copy(isStopConfirmVisible = true) }
        else sendEffect(VideoCompressRunEffect.NavigateBack)
    }

    private fun onStopConfirmed() {
        runJob?.cancel()
        setState { withStopped() }
    }

    /** Names only the originals whose smaller copy actually exists — never a video that failed or was skipped. */
    private fun onDeleteOriginalsConfirmed() {
        val run = currentState.run ?: return
        runDelete(targetsFor(run.succeededIds))
    }

    private fun onConsentResult(granted: Boolean) {
        val ids = pendingDeleteIds
        pendingDeleteIds = emptySet()
        if (granted && ids.isNotEmpty()) runDelete(targetsFor(ids))
    }

    private fun targetsFor(ids: Collection<String>): List<ScannedFile> =
        currentState.videos.filter { it.id in ids }.map { it.file }

    /** The delete round trip `VideoManagerViewModel` already has, reused whole. */
    private fun runDelete(targets: List<ScannedFile>) {
        val requireTrash = currentState.trashEligible
        setState { copy(isDeleteConfirmVisible = false) }
        if (targets.isEmpty()) return
        pendingDeleteIds = targets.map { it.id }.toSet()
        launchSafely(onError = { setState { withFailure(it) } }) {
            when (val result = deleteFiles(targets, FeatureId.VideoCompressor, requireTrash = requireTrash)) {
                is AppResult.Failure -> setState { withFailure(result.error) }
                is AppResult.Success -> reduceDelete(result.value)
            }
        }
    }

    private fun reduceDelete(outcome: DeleteOutcome) {
        when (outcome) {
            // Gated on freedBytes > 0: never hand the clean-result screen a figure nobody produced.
            is DeleteOutcome.Deleted -> {
                pendingDeleteIds = emptySet()
                setState { copy(reclaimedBytes = outcome.freedBytes) }
                if (outcome.freedBytes > 0L) {
                    sendEffect(
                        VideoCompressRunEffect.NavigateToCleanResult(
                            cleanupSummaryFor(FeatureId.VideoCompressor, outcome),
                        ),
                    )
                }
            }
            // The normal path, not an error (`LLM.md` §7.4).
            is DeleteOutcome.PendingConsent -> {
                pendingDeleteIds = outcome.ids.toSet()
                sendEffect(VideoCompressRunEffect.RequestDeleteConsent(outcome.request))
            }
            // A first-class outcome, never silence — mirrors `RemoveFindingUseCase`'s own mapping.
            DeleteOutcome.NothingResolved -> {
                pendingDeleteIds = emptySet()
                setState { withFailure(AppError.NotFound()) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        runJob?.cancel()
    }

    companion object {
        /** Must equal `Route.VideoCompressRun`'s property names (phase 08); a rename is silent. */
        const val VIDEO_IDS_ARG: String = "videoIds"
        const val PRESET_ARG: String = "preset"
        const val CODEC_ARG: String = "codec"
    }
}
