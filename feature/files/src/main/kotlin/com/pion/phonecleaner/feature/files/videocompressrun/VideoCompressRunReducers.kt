package com.pion.phonecleaner.feature.files.videocompressrun

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import kotlinx.collections.immutable.ImmutableList

/**
 * The pure half of `videocompressrun`: state in, state out (plus the route-argument readers), no
 * coroutine and no repository — split out of the ViewModel so neither file exceeds the size rule
 * (`LLM.md` §4), mirroring `VideoManagerReducers.kt` and `VideoCompressorReducers.kt`.
 */

/** The rows resolved; empty is [VideoCompressRunState.sessionLost], a state, never a crash. */
internal fun VideoCompressRunState.withLoaded(
    loaded: ImmutableList<VideoCandidate>,
): VideoCompressRunState = copy(videos = loaded, sessionLost = loaded.isEmpty(), error = null)

/**
 * Closes `run.total` onto what actually settled, exactly as the natural end of the flow does, so
 * `isFinished` becomes true and the finished outputs stay reachable by the delete step. Do NOT null
 * `run` — unlike the photo screen, the completed videos still need their originals deleted.
 */
internal fun VideoCompressRunState.withStopped(): VideoCompressRunState {
    val stopped = run?.copy(total = run.settled)
    return copy(isStopConfirmVisible = false, run = stopped, producedBytes = stopped?.savedBytes ?: producedBytes)
}

/** Lowers every re-entry guard the call raised; `run` is left as-is — the finished items are real. */
internal fun VideoCompressRunState.withFailure(error: AppError): VideoCompressRunState = copy(
    isRunConfirmVisible = false,
    isStopConfirmVisible = false,
    isDeleteConfirmVisible = false,
    error = error,
)

/**
 * A `List<String>` route argument does not have one settled representation in a `SavedStateHandle`:
 * type-safe navigation may store it as an `Array<String>` or as a `List<*>`. Both are read here rather
 * than guessing which `:app` will use (phase 08) — a wrong guess is an empty screen with no error,
 * mirroring `CompressRunViewModel.readPhotoIds`.
 */
internal fun SavedStateHandle.readVideoIds(key: String): List<String> = when (val raw = get<Any?>(key)) {
    is Array<*> -> raw.filterIsInstance<String>()
    is List<*> -> raw.filterIsInstance<String>()
    else -> emptyList()
}

/**
 * An enum route argument arrives as the **enum constant itself**, not as its name.
 *
 * Type-safe navigation serialises a `@Serializable` enum property into the back stack and hands it
 * back through `SavedStateHandle` already decoded, so `get<String>(key)` is an implicit cast on a
 * `VideoQualityPreset` and throws. It cost a real crash on device (Samsung SM-A165F, 2026-09-07):
 * `ClassCastException: VideoQualityPreset cannot be cast to java.lang.String`, the moment the picker
 * navigated here. Nothing caught it earlier — the module compiles, and Koin's `checkModules()` never
 * builds this ViewModel with a populated handle.
 *
 * Both shapes are read rather than guessing which one the platform will hand over, exactly as
 * [readVideoIds] above does for its list, and for the same reason: a wrong guess here is a crash, not
 * a degraded screen. The `String` arm also covers a value restored from a process-death bundle.
 *
 * Falls back to [VideoQualityPreset.Default] when absent or unknown. The screen does not re-probe the
 * device: the picker already disabled what this hardware cannot encode.
 */
internal fun SavedStateHandle.readPreset(key: String): VideoQualityPreset =
    when (val raw = get<Any?>(key)) {
        is VideoQualityPreset -> raw
        is String -> VideoQualityPreset.entries.firstOrNull { it.name == raw }
        else -> null
    } ?: VideoQualityPreset.Default

/** Same shape as [readPreset], for the same reason. */
internal fun SavedStateHandle.readCodec(key: String): VideoCodecOption =
    when (val raw = get<Any?>(key)) {
        is VideoCodecOption -> raw
        is String -> VideoCodecOption.entries.firstOrNull { it.name == raw }
        else -> null
    } ?: VideoCodecOption.Default
