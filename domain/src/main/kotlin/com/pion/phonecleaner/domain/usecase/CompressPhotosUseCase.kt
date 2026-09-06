package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.photo.CompressStep
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.CompressedPhotoLedger
import com.pion.phonecleaner.domain.repository.PhotoCompressor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * The run itself (`docs/screens/13-photo-and-media.md` §4.2).
 *
 * The quality and the maximum edge are the port's named defaults, so the ViewModel passes no
 * literals and `:feature` never needs to see `:data` (see `PhotoCompressor`).
 */
class CompressPhotosUseCase(
    private val compressor: PhotoCompressor,
    private val compressed: CompressedPhotoLedger,
) {
    /**
     * Each photo that was actually rewritten is recorded as it lands, not in one batch at the end:
     * a run cancelled halfway has still shrunk every photo it got to, and those are precisely the
     * rows the picker must keep offering (`LoadCompressiblePhotosUseCase`). A completion-time write
     * would never run on the cancel path, so the photos the user can see were compressed would be
     * the ones that disappear.
     *
     * A `failed` step wrote nothing, and a step that saved nothing was skipped rather than written
     * (`PhotoCompressor`) — neither is a compressed photo, so neither is recorded.
     */
    operator fun invoke(ids: List<PhotoId>): Flow<CompressStep> = compressor.compress(ids)
        .onEach { step -> if (!step.failed && step.savedBytes > 0L) compressed.record(step.id) }
}
