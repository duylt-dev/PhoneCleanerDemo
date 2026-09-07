package com.pion.phonecleaner.feature.files.videocompressor

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.core.mvi.SelectableFiles
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.repository.AnalyticsEvent
import com.pion.phonecleaner.domain.repository.AnalyticsRepository
import com.pion.phonecleaner.domain.repository.VideoEncoderCapabilities
import com.pion.phonecleaner.domain.usecase.EstimateVideoCompressionUseCase
import com.pion.phonecleaner.domain.usecase.LoadCompressibleVideosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.feature.files.component.MediaAccess
import com.pion.phonecleaner.feature.files.component.restoreSelection
import com.pion.phonecleaner.feature.files.component.selectableVideos
import com.pion.phonecleaner.feature.files.component.storeSelection
import kotlin.time.Duration.Companion.seconds
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `videocompressor` (`plans/260907-0142-video-compression/phase-06-picker-screen.md` step 2).
 *
 * `init` resolves only [VideoCompressorState.isHevcAvailable], which needs no permission — a
 * deliberate exception to key insight 1. The *load* still waits for
 * [VideoCompressorIntent.PermissionResolved], because it cannot run without a grant.
 */
class VideoCompressorViewModel(
    private val savedState: SavedStateHandle,
    private val loadCompressibleVideos: LoadCompressibleVideosUseCase,
    private val estimateCompression: EstimateVideoCompressionUseCase,
    private val capabilities: VideoEncoderCapabilities,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    private val analytics: AnalyticsRepository,
    log: AppLogger,
) : MviViewModel<VideoCompressorState, VideoCompressorIntent, VideoCompressorEffect>(
    VideoCompressorState(
        preset = savedState.restoredPreset(PRESET_KEY),
        codec = savedState.restoredCodec(CODEC_KEY),
    ),
    log,
) {

    private var loadJob: Job? = null

    init {
        launchSafely(onError = ::onFailure) {
            val hevcAvailable = capabilities.isSupported(VideoCodecOption.Hevc)
            setState { copy(isHevcAvailable = hevcAvailable) }
        }
    }

    override fun onIntent(intent: VideoCompressorIntent) {
        when (intent) {
            VideoCompressorIntent.ScreenStarted -> onScreenStarted()
            is VideoCompressorIntent.PermissionResolved -> onAccess(intent.access)
            VideoCompressorIntent.GrantMorePressed ->
                sendEffect(VideoCompressorEffect.RequestMediaPermission)

            VideoCompressorIntent.StartPressed -> onStartPressed()
            is VideoCompressorIntent.SortSelected -> setState { withSort(intent.sort) }
            is VideoCompressorIntent.PresetSelected -> onPresetSelected(intent.preset)
            is VideoCompressorIntent.CodecSelected -> onCodecSelected(intent.codec)
            is VideoCompressorIntent.RowToggled -> select { toggle(intent.id) }
            VideoCompressorIntent.SelectAllToggled -> onSelectAllToggled()
            VideoCompressorIntent.CompletionAnimationFinished ->
                setState { copy(phase = ToolPhase.Ready) }

            VideoCompressorIntent.ContinuePressed -> onContinuePressed()
            VideoCompressorIntent.BackPressed -> onBackPressed()
        }
    }

    /**
     * `ErrorCard`'s retry raises this same intent, and a tap — unlike an `ON_START` — re-delivers no
     * [MediaAccess]. On `phase != Idle` alone that button is drawn, enabled and inert, backgrounding
     * the app the only way to rescan: `LLM.md` §11 row 11, the row asking for this shape to be checked
     * rather than copied. `BlurryPhotosViewModel` carries the same two clauses. Opening is still not
     * compressing, and the open/used pair stays on the first entry — a retry is the same visit.
     */
    private fun onScreenStarted() {
        val state = currentState
        when {
            state.phase == ToolPhase.Idle -> {
                analytics.track(AnalyticsEvent.FeatureOpened(FeatureId.VideoCompressor))
                launchSafely { markFeatureUsed(FeatureId.VideoCompressor) }
            }

            state.phase == ToolPhase.Ready && state.error != null && state.access.canLoad -> load()
            else -> Unit
        }
    }

    /** Dismisses the panel. `ON_START` already loads if allowed; this is a defensive fallback. */
    private fun onStartPressed() {
        setState { copy(introVisible = false) }
        if (currentState.phase == ToolPhase.Idle && currentState.access.canLoad) load()
    }

    /** Every `ON_START`. `Denied` does NOT re-prompt — `VideoManagerViewModel.onAccess`, verbatim. */
    private fun onAccess(access: MediaAccess) {
        val changed = access != currentState.access
        setState { copy(access = access) }
        if (!access.canLoad) {
            loadJob?.cancel()
            setState { copy(phase = ToolPhase.Ready, videos = selectableVideos()) }
            return
        }
        if (changed || currentState.videos.items.isEmpty()) load()
    }

    /** `withTimeoutOrNull`, never `withTimeout`: the expiry lands in the same `when` as success. */
    private fun load() {
        loadJob?.cancel()
        setState { copy(phase = ToolPhase.Scanning, error = null) }
        loadJob = launchSafely(onError = ::onFailure) {
            when (val result = withTimeoutOrNull(QueryTimeout) { loadCompressibleVideos() }) {
                null -> onFailure(AppError.Unexpected(TimeoutMessage))
                is AppResult.Failure -> onFailure(result.error)
                is AppResult.Success -> {
                    setState { withLoaded(result.value, savedState.restoreSelection()) }
                    reestimate()
                }
            }
        }
    }

    /** Set arithmetic in the reducer, a save, then a synchronous re-estimate — no coroutine here. */
    private fun select(transform: SelectableFiles<VideoCandidate>.() -> SelectableFiles<VideoCandidate>) {
        setState { copy(videos = videos.transform()) }
        savedState.storeSelection(currentState.videos.selectedIds)
        reestimate()
    }

    /** Selects everything except `alreadyCompressed` rows (D7); pure toggle lives in the reducers. */
    private fun onSelectAllToggled() {
        val selectable = currentState.selectableIds()
        select { selectAllSkipping(selectable) }
    }

    private fun onPresetSelected(preset: VideoQualityPreset) {
        setState { copy(preset = preset) }
        savedState[PRESET_KEY] = preset.name
        reestimate()
    }

    /** Guarded: a `SavedStateHandle` restored on different hardware could otherwise pick a codec the
     * device cannot encode — the chip itself is disabled, so a normal tap never reaches this. */
    private fun onCodecSelected(codec: VideoCodecOption) {
        if (codec == VideoCodecOption.Hevc && !currentState.isHevcAvailable) return
        setState { copy(codec = codec) }
        savedState[CODEC_KEY] = codec.name
        reestimate()
    }

    /** Pure arithmetic over data already in state — synchronous, no coroutine (key insight 3). */
    private fun reestimate() {
        val selection = currentState.videos.selectedItems()
        setState { copy(estimate = selection.ifEmpty { null }?.let { estimateCompression(it, preset, codec) }) }
    }

    private fun onContinuePressed() {
        if (!currentState.canContinue) return
        sendEffect(
            VideoCompressorEffect.OpenVideoCompressRun(
                ids = currentState.videos.selectedIds.toImmutableList(),
                preset = currentState.preset,
                codec = currentState.codec,
            ),
        )
    }

    /** Back during a load leaves; it does not block with a toast. */
    private fun onBackPressed() {
        loadJob?.cancel()
        sendEffect(VideoCompressorEffect.NavigateBack)
    }

    private fun onFailure(error: AppError) {
        setState { copy(phase = ToolPhase.Ready, error = error) }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
    }

    companion object {
        /** Preset/codec across process death; an unknown restored name falls back to `Default`. */
        const val PRESET_KEY: String = "videoCompressorPreset"
        const val CODEC_KEY: String = "videoCompressorCodec"
        private val QueryTimeout = 30.seconds
        private const val TimeoutMessage = "Video scan timed out"
    }
}
