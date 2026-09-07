package com.pion.phonecleaner.feature.files.videocompressor

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.FileToolIntent
import com.pion.phonecleaner.core.mvi.FileToolState
import com.pion.phonecleaner.core.mvi.SelectableFiles
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressionEstimate
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.feature.files.component.MediaAccess
import com.pion.phonecleaner.feature.files.component.MediaSort
import com.pion.phonecleaner.feature.files.component.selectableVideos
import kotlinx.collections.immutable.ImmutableList

/**
 * `videocompressor` — the picker (`plans/260907-0142-video-compression/phase-06-picker-screen.md`).
 *
 * **The picker only *chooses*. Nothing is re-encoded here.** `ContinuePressed` hands the chosen ids
 * and the chosen preset/codec to `videocompressrun` (phase 07), which owns every byte written.
 *
 * **This screen inherits `video`'s permission funnel, not `compressor`'s absence of one** (key
 * insight 1). The grant is modelled in [VideoCompressorState.access], and the load starts from
 * [VideoCompressorIntent.PermissionResolved] — never from `init`.
 *
 * **[VideoCompressorState.isHevcAvailable] is state, not a composable's business (D6).** The HEVC
 * chip is always drawn; when this is `false` it is drawn disabled with
 * `video_compress_codec_hevc_unavailable` beneath it. A composable that asked the question itself
 * would need `VideoEncoderCapabilities`, which a `:feature` module may not see (`LLM.md` §2), and a
 * screen that computes its own enablement is a screen no ViewModel test can assert — which Phase 09
 * has to exercise, because our test device carries a hardware HEVC encoder and can never produce the
 * disabled state on its own.
 *
 * **The estimate is arithmetic, and `null` means "not measured yet."** [VideoCompressorState.estimate]
 * is recomputed synchronously on every selection, preset and codec change — `EstimateVideoCompressionUseCase`
 * is pure integer arithmetic over `SIZE`/`DURATION` already in memory, unlike the photo screen's
 * sampled encoder pass. The screen renders nothing at all when it is unusable; it is never presented
 * as a measurement.
 */
@Immutable
data class VideoCompressorState(
    override val phase: ToolPhase = ToolPhase.Idle,
    /** The landing panel. `StartPressed` dismisses it. */
    val introVisible: Boolean = true,
    val videos: SelectableFiles<VideoCandidate> = selectableVideos(),
    val access: MediaAccess = MediaAccess.Unknown,
    val sort: MediaSort = MediaSort.NewestFirst,
    val preset: VideoQualityPreset = VideoQualityPreset.Default,
    val codec: VideoCodecOption = VideoCodecOption.Default,
    /**
     * Whether this device has a **hardware** HEVC encoder ([VideoEncoderCapabilities]). Default
     * `false` so the chip can only move disabled → enabled, never the reverse — an option the user
     * had already tapped must never turn itself off (key insight 6).
     */
    val isHevcAvailable: Boolean = false,
    /** Arithmetic over the selection's own SIZE and DURATION. `null` before anything is selected. */
    val estimate: VideoCompressionEstimate? = null,
    override val error: AppError? = null,
) : FileToolState {

    val selectedCount: Int get() = videos.selectedCount

    val selectedBytes: Long
        get() = videos.items.sumOf { if (it.id in videos.selectedIds) it.sizeBytes else 0L }

    /** An empty selection cannot reach the run screen: the button is disabled, not a silent no-op. */
    val canContinue: Boolean get() = phase == ToolPhase.Ready && selectedCount > 0

    val showEmptyState: Boolean
        get() = phase == ToolPhase.Ready && videos.items.isEmpty() && access.canLoad

    val showPartialAccessBanner: Boolean get() = access == MediaAccess.Partial

    val showPermissionState: Boolean get() = access == MediaAccess.Denied

    val isBusy: Boolean get() = phase == ToolPhase.Scanning

    /** The HEVC chip's own reason line. Read by the composable; never derived by it (D6). */
    val showHevcUnavailableReason: Boolean get() = !isHevcAvailable
}

sealed interface VideoCompressorIntent : UiIntent {
    data object ScreenStarted : VideoCompressorIntent, FileToolIntent.Rescan

    /** The load starts from HERE, never from `init` (MVI §3, key insight 1). */
    data class PermissionResolved(val access: MediaAccess) : VideoCompressorIntent
    data object GrantMorePressed : VideoCompressorIntent
    data object StartPressed : VideoCompressorIntent
    data class SortSelected(val sort: MediaSort) : VideoCompressorIntent
    data class PresetSelected(val preset: VideoQualityPreset) : VideoCompressorIntent
    data class CodecSelected(val codec: VideoCodecOption) : VideoCompressorIntent
    data class RowToggled(override val id: String) : VideoCompressorIntent, FileToolIntent.ToggleItem
    data object SelectAllToggled : VideoCompressorIntent, FileToolIntent.ToggleSelectAll
    data object CompletionAnimationFinished : VideoCompressorIntent
    data object ContinuePressed : VideoCompressorIntent
    data object BackPressed : VideoCompressorIntent
}

sealed interface VideoCompressorEffect : UiEffect {
    data object RequestMediaPermission : VideoCompressorEffect

    /**
     * **Scalars, and no object payload** — `PhotoCompressorContract.OpenCompressRun(ids)` is the
     * precedent (`LLM.md` §7.2): scalars and enums survive process death through `SavedStateHandle`,
     * a bundled request object does not, and a `VideoRunRequest(ids, preset, codec)` wrapper would
     * buy nothing the three arguments do not already give. `videocompressrun` re-reads the rows from
     * `MediaStore` by id, so process death re-materialises the run screen instead of emptying a
     * static field.
     */
    data class OpenVideoCompressRun(
        val ids: ImmutableList<String>,
        val preset: VideoQualityPreset,
        val codec: VideoCodecOption,
    ) : VideoCompressorEffect

    data object NavigateBack : VideoCompressorEffect
}
