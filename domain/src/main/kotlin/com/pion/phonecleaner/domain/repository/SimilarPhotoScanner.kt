package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.photo.SimilarScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * The similar-photo engine (`docs/screens/13-photo-and-media.md` §0.3).
 *
 * It **composes** the shared primitives rather than walking storage itself
 * (`docs/system-architecture.md` §4.5): the rows come from `PhotoRepository`, the hash from
 * `PerceptualHasher`, and the delete — when one happens — from the one `FileDeleter`.
 *
 * Cancellation is the collector's: the flow is collected inside the ViewModel's `scanJob`, so
 * cancelling that job stops the hashing children with it (§1.2).
 */
interface SimilarPhotoScanner {
    fun scan(): Flow<SimilarScanProgress>
}
