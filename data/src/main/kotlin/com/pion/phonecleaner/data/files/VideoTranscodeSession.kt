package com.pion.phonecleaner.data.files

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.pion.phonecleaner.core.common.log.AppLogger
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The only file that builds a `Transformer` besides [Media3VideoCompressor]'s effect wiring
 * (`phase-04-data-media3-engine.md` step 3). `androidx.media3` is imported here and in
 * `Media3VideoCompressor.kt` and nowhere else in the project (`LLM.md` §3.6/§4's two-file rule).
 *
 * **One `HandlerThread` per call, not per instance.** This class is held by a Koin `single`
 * ([Media3VideoCompressor], `filesDataModule`); a process-long thread for a feature used occasionally
 * would be a leak with a nice name. `quitSafely()` runs in [transcode]'s `finally`.
 *
 * **Every `Transformer` call — `start`, `cancel`, `getProgress` — goes through the one [Handler]
 * bound to that thread's looper.** `Transformer.start`/`cancel`/`getProgress` all throw
 * `IllegalStateException` off the application thread (`Transformer.java:1147-1154`, verified in
 * `reports/researcher-01-media3-transformer-api.md` §8, §10).
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal class VideoTranscodeSession(
    private val context: Context,
    private val log: AppLogger,
) {

    /**
     * @param targetShortSidePx `null` ⇒ no [Presentation] effect at all
     * ([com.pion.phonecleaner.domain.policy.VideoScaling] is the only legal source of this argument;
     * passing the source's own size here would still upscale, because `Presentation.configure()` has
     * no guard of its own).
     * @param videoMimeType [com.pion.phonecleaner.domain.model.video.VideoCodecOption.mimeType] —
     * resolved by the caller. This function holds no `when (codec)` of its own.
     * @param videoBitrateBps `preset.bitrateBps(codec)` — resolved by the caller, for the same reason.
     * @return the output file's real length, read with [File.length] — **never**
     * `ExportResult.fileSizeBytes`, which is `C.LENGTH_UNSET` when the muxer did not report it
     * (`ExportResult.java:457`) and would otherwise pass the "is it smaller" guard as `-1`.
     * @throws ExportException on an engine failure. `CancellationException` propagates from the
     * suspension point itself when the caller cancels; it is never caught here.
     */
    suspend fun transcode(
        source: Uri,
        outputPath: String,
        targetShortSidePx: Int?,
        videoMimeType: String,
        videoBitrateBps: Int,
        onProgress: (Int) -> Unit,
    ): Long {
        val thread = HandlerThread("VideoTranscode").apply { start() }
        val handler = Handler(thread.looper)
        var poll: Runnable? = null
        try {
            return suspendCancellableCoroutine { cont ->
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        // UNKNOWN (not verified): whether onFallbackApplied fires before onCompleted,
                        // and on which thread. Logged, never claimed to the user.
                        log.d {
                            "export done bitrate=${result.averageVideoBitrate} " +
                                "width=${result.width} height=${result.height}"
                        }
                        if (cont.isActive) cont.resume(File(outputPath).length())
                    }

                    override fun onError(
                        composition: Composition,
                        result: ExportResult,
                        exception: ExportException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(exception)
                    }
                }

                val effects = if (targetShortSidePx == null) {
                    Effects.EMPTY
                } else {
                    Effects(emptyList(), listOf(Presentation.createForShortSide(targetShortSidePx)))
                }
                // Audio is not touched: no setAudioMimeType, no audio processors, so Transformer
                // transmuxes it unchanged regardless of the video codec chosen.
                val item = EditedMediaItem.Builder(MediaItem.fromUri(source)).setEffects(effects).build()
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder().setBitrate(videoBitrateBps).build(),
                    )
                    .build() // setEnableFallback stays at its default true
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(videoMimeType)
                    .setEncoderFactory(encoderFactory)
                    .setLooper(thread.looper)
                    .addListener(listener)
                    .build()

                cont.invokeOnCancellation {
                    // Posted, not called directly: cancel() throws off the Transformer's own thread,
                    // and this lambda can run on any thread the cancelling coroutine happens to be on.
                    handler.post {
                        runCatching { transformer.cancel() }
                            .onFailure { log.e(it) { "cancel() failed for $outputPath" } }
                        // cancel() does NOT delete the output file (Transformer.java:1147-1154) — the
                        // caller's guarantee that a cancelled run leaves no partial file starts here.
                        File(outputPath).delete()
                    }
                }

                handler.post {
                    poll = startProgressPolling(transformer, handler, onProgress)
                    transformer.start(item, outputPath)
                }
            }
        } finally {
            // Only the recurring poll Runnable is removed — never a blanket
            // removeCallbacksAndMessages(null), which would race the cancellation cleanup just
            // posted above and could drop it before the handler thread runs it. quitSafely() still
            // lets that already-queued post run before the looper actually stops.
            poll?.let(handler::removeCallbacks)
            thread.quitSafely()
        }
    }

    /** Polls every [PROGRESS_POLL_MS]; reports only on `PROGRESS_STATE_AVAILABLE`, per Transformer's
     * own contract — `WAITING_FOR_AVAILABILITY`/`UNAVAILABLE` report nothing, so the screen shows an
     * indeterminate state rather than a number the engine did not produce. */
    private fun startProgressPolling(
        transformer: Transformer,
        handler: Handler,
        onProgress: (Int) -> Unit,
    ): Runnable {
        val holder = ProgressHolder()
        lateinit var poll: Runnable
        poll = Runnable {
            val state = transformer.getProgress(holder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) onProgress(holder.progress)
            if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                handler.postDelayed(poll, PROGRESS_POLL_MS)
            }
        }
        handler.postDelayed(poll, PROGRESS_POLL_MS)
        return poll
    }

    private companion object {
        const val PROGRESS_POLL_MS = 500L
    }
}
