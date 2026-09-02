package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.photo.CompressionEstimate
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.PhotoCompressor

/**
 * What the intro panel shows, **measured** (`docs/screens/13-photo-and-media.md` §3.2, §3.5).
 *
 * The competitor's panel is the literal string *"Before 807KB / After 484KB(-40%)"* next to a
 * claim of *"up to about 40%"*, and its engine's own scale rule produces neither figure. This
 * re-encodes a sample through the real encoder, in memory, and writes nothing.
 */
class EstimateCompressionUseCase(
    private val compressor: PhotoCompressor,
) {
    suspend operator fun invoke(ids: List<PhotoId>): AppResult<CompressionEstimate> =
        compressor.estimate(ids)
}
