package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.map
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.repository.CompressedVideoLedger
import com.pion.phonecleaner.domain.repository.VideoCandidateRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The compressor's candidate list (`phase-03-domain-video-compression.md` step 9, D7).
 *
 * Mirrors `LoadCompressiblePhotosUseCase` character for character on the filter, and for the same
 * reason: a successful transcode routinely drops a video below the floor, so the size filter alone
 * removes from the next scan exactly the videos the user just compressed — the list silently
 * shrinks after every run, with nothing on screen that explains it. Drop the
 * `|| it.id in known` half and the feature deletes its own evidence.
 *
 * **Where this diverges from the photo side, deliberately:** the row keeps
 * `alreadyCompressed = true`, the picker draws a label on it, and **select-all skips it**. A photo
 * needs no such skip because the photo engine simply refuses to write a re-encode that is not
 * smaller; a video re-encoded a second time is **real generation loss even when the output is
 * smaller**. The user can still select such a row by hand — only select-all passes it over.
 */
class LoadCompressibleVideosUseCase(
    private val candidates: VideoCandidateRepository,
    private val ledger: CompressedVideoLedger,
) {
    suspend operator fun invoke(
        minBytes: Long = MIN_COMPRESSIBLE_BYTES,
    ): AppResult<ImmutableList<VideoCandidate>> {
        val known = ledger.compressedIds()
        return candidates.candidates().map { rows ->
            rows.filter { it.sizeBytes >= minBytes || it.id in known }
                .map { it.copy(alreadyCompressed = it.id in known) }
                .toImmutableList()
        }
    }

    companion object {
        /**
         * 5 MiB. Below it a transcode costs the user tens of seconds for a saving of a megabyte or
         * two, and the row costs a decision for nothing.
         *
         * Ours; no source states it — the same voice
         * `LoadCompressiblePhotosUseCase.MIN_COMPRESSIBLE_BYTES` uses for its own 200 KiB floor.
         *
         * **Measured, then lowered.** The first value was 20 MiB. On the test device (Samsung
         * SM-A165F, 2026-09-07) that showed **1 of 25 videos** — the other 24 were downloaded clips
         * and messenger files under the floor, so the screen read as broken rather than as selective.
         * 5 MiB keeps out the genuinely trivial clip while letting an ordinary recording through.
         * A parameter, so a test and a later decision can move it without touching the call site.
         */
        const val MIN_COMPRESSIBLE_BYTES: Long = 5L * 1024L * 1024L
    }
}
