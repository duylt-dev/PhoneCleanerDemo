package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.photo.CompressStep
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.PhotoCompressor
import kotlinx.coroutines.flow.Flow

/**
 * The run itself (`docs/screens/13-photo-and-media.md` §4.2).
 *
 * The quality and the maximum edge are the port's named defaults, so the ViewModel passes no
 * literals and `:feature` never needs to see `:data` (see `PhotoCompressor`).
 */
class CompressPhotosUseCase(
    private val compressor: PhotoCompressor,
) {
    operator fun invoke(ids: List<PhotoId>): Flow<CompressStep> = compressor.compress(ids)
}
