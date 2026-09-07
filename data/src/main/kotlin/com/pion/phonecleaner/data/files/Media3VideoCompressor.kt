@file:OptIn(UnstableApi::class)

package com.pion.phonecleaner.data.files

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCodecOption
import com.pion.phonecleaner.domain.model.video.VideoCompressOutcome
import com.pion.phonecleaner.domain.model.video.VideoCompressProgress
import com.pion.phonecleaner.domain.model.video.VideoCompressStep
import com.pion.phonecleaner.domain.model.video.VideoQualityPreset
import com.pion.phonecleaner.domain.policy.VideoScaling
import com.pion.phonecleaner.domain.repository.VideoCandidateRepository
import com.pion.phonecleaner.domain.repository.VideoCompressor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn

/**
 * The re-encoder (`phase-04-data-media3-engine.md` step 3, D5/D6). `androidx.media3` is imported here
 * and in `VideoTranscodeSession.kt` and nowhere else in the project (`LLM.md` §3.6/§4).
 *
 * **Sequential, one video at a time** — a `Transformer` is driven from a single application thread
 * and concurrent instances are unsupported ([VideoCompressor]'s own KDoc; `LLM.md` §11 row 12 already
 * records that `limitedParallelism` would bound nothing here even if someone reached for it).
 *
 * **Per-video state machine**, every arrow ending in a deleted temp file or a published row, never
 * neither: transcode → temp file → (throws ⇒ delete temp, `Failed`) | (not smaller ⇒ delete temp,
 * `NotSmaller`) | (smaller ⇒ publish → delete temp, `Compressed` or `Failed`). Cancellation is handled
 * inside [VideoTranscodeSession.transcode] itself, on the Transformer's own thread.
 *
 * A `channelFlow`, not a plain `flow`: [VideoTranscodeSession]'s progress callback fires from its own
 * `HandlerThread`, never from this flow's collecting coroutine, and a cold `flow { emit(...) }`
 * throws `IllegalStateException` ("Flow invariant is violated") the first time `emit` is called off
 * that coroutine. `trySend` on a channel has no such restriction.
 */
internal class Media3VideoCompressor(
    private val context: Context,
    private val candidates: VideoCandidateRepository,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : VideoCompressor {

    override fun compress(
        ids: List<String>,
        preset: VideoQualityPreset,
        codec: VideoCodecOption,
    ): Flow<VideoCompressProgress> = channelFlow {
        val session = VideoTranscodeSession(context, log)
        val publisher = VideoOutputPublisher(context, log)
        // Whatever an earlier run left behind when its process was killed mid-transcode. Nothing else
        // ever visits that directory, and the files are app-private, so this is the only sweep there is.
        publisher.sweepAbandonedTempFiles()
        when (val resolved = candidates.rowsFor(ids)) {
            is AppResult.Failure -> {
                log.e { "Compression could not read the selection: ${resolved.error}" }
                ids.forEachIndexed { index, id ->
                    send(VideoCompressProgress.Finished(failedStep(index + 1, ids.size, id)))
                }
            }

            is AppResult.Success -> {
                val videos = resolved.value
                videos.forEachIndexed { index, candidate ->
                    val step = runOne(session, publisher, index + 1, videos.size, candidate, preset, codec)
                    send(VideoCompressProgress.Finished(step))
                }
            }
        }
    }.flowOn(dispatchers.io)

    private suspend fun ProducerScope<VideoCompressProgress>.runOne(
        session: VideoTranscodeSession,
        publisher: VideoOutputPublisher,
        index: Int,
        total: Int,
        candidate: VideoCandidate,
        preset: VideoQualityPreset,
        codec: VideoCodecOption,
    ): VideoCompressStep {
        val temp = publisher.newTempFile(candidate.file.name, preset)
        val afterBytes = try {
            session.transcode(
                source = Uri.parse(candidate.id),
                outputPath = temp.absolutePath,
                // VideoScaling is the ONLY legal source of this argument — never the raw preset value.
                targetShortSidePx = VideoScaling.targetShortSideOrNull(candidate.width, candidate.height, preset),
                videoMimeType = codec.mimeType,
                videoBitrateBps = preset.bitrateBps(codec),
                onProgress = { percent ->
                    trySend(VideoCompressProgress.Working(index, total, candidate.id, percent))
                },
            )
        } catch (cancellation: CancellationException) {
            publisher.discard(temp)
            throw cancellation // NEVER swallow this — it is how a cancelled run is told to stop.
        } catch (failure: Exception) {
            log.e(failure) { "Transcode failed for ${candidate.id}" }
            publisher.discard(temp)
            return failedStep(index, total, candidate.id, candidate.sizeBytes)
        }

        // The empty-output guard. `savedBytes` is `beforeBytes - afterBytes`, so a zero-length file
        // reports the WHOLE original as saved, `canDeleteOriginals` turns true, and the delete step
        // offers to remove the source in exchange for nothing. The upper bound alone does not catch it.
        if (afterBytes <= 0L) {
            log.e { "Transcode reported success but produced an empty file for ${candidate.id}" }
            publisher.discard(temp)
            return failedStep(index, total, candidate.id, candidate.sizeBytes)
        }

        // The bigger-output guard: Transformer has no such check. Measured with File.length(), never
        // ExportResult.fileSizeBytes, which can be C.LENGTH_UNSET.
        if (afterBytes >= candidate.sizeBytes) {
            publisher.discard(temp)
            return VideoCompressStep(
                index = index,
                total = total,
                id = candidate.id,
                beforeBytes = candidate.sizeBytes,
                afterBytes = afterBytes,
                outcome = VideoCompressOutcome.NotSmaller,
            )
        }

        // Outside a try this would abandon the temp file AND end the entire run at this video:
        // publishing throws IllegalArgumentException/SecurityException, not only IOException, when an
        // OEM provider rejects RELATIVE_PATH. The port's contract is one failed STEP, never a dead run.
        val outputId = try {
            publisher.publish(temp, displayNameFor(candidate, preset))
        } catch (cancellation: CancellationException) {
            publisher.discard(temp)
            throw cancellation
        } catch (failure: Exception) {
            log.e(failure) { "Publish threw for ${candidate.id}" }
            null
        }
        publisher.discard(temp)
        if (outputId == null) {
            log.e { "Publish failed for ${candidate.id}" }
            return failedStep(index, total, candidate.id, candidate.sizeBytes)
        }
        return VideoCompressStep(
            index = index,
            total = total,
            id = candidate.id,
            beforeBytes = candidate.sizeBytes,
            afterBytes = afterBytes,
            outcome = VideoCompressOutcome.Compressed,
            outputId = outputId,
        )
    }

    private fun displayNameFor(candidate: VideoCandidate, preset: VideoQualityPreset): String {
        val base = candidate.file.name.substringBeforeLast('.', candidate.file.name).ifBlank { "video" }
        return "${base}_${preset.shortSidePx}p.mp4"
    }

    private fun failedStep(index: Int, total: Int, id: String, beforeBytes: Long = 0L): VideoCompressStep =
        VideoCompressStep(index, total, id, beforeBytes, beforeBytes, VideoCompressOutcome.Failed)
}
