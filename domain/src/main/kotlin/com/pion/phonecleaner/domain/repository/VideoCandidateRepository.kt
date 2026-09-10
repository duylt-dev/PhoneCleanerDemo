package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import kotlinx.collections.immutable.ImmutableList

/**
 * The compressor's read surface (`phase-03-domain-video-compression.md` step 8).
 *
 * **Not** `MediaStoreRepository.videos()` widened. That port is shared by six tools and returns
 * `ScannedFile`; this one needs duration and dimensions, which those six do not. Both must come out
 * of the one query builder in `:data/storage` (`LLM.md` §12's mandatory mitigation) — Phase 04
 * extends `MediaStoreQuery`, it does not open a second cursor.
 */
interface VideoCandidateRepository {
    suspend fun candidates(): AppResult<ImmutableList<VideoCandidate>>

    /**
     * The rows for [ids], **in the order given**. The caller's order is the user's order; filtering
     * the library and keeping its own order is a different list. Same rule
     * `CompressRunViewModel.onLoaded` states, and the reason `PhotoRepository.rowsFor` exists.
     */
    suspend fun rowsFor(ids: List<String>): AppResult<ImmutableList<VideoCandidate>>
}
