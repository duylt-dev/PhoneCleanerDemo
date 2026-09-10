package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.photo.PhotoId

/**
 * Which photos this app has already re-encoded (`docs/screens/13-photo-and-media.md` §3.2).
 *
 * It exists for one reason: **a photo must not vanish from the compressor's list because the
 * compressor did its job.** The candidate query keeps only rows at or above
 * `LoadCompressiblePhotosUseCase.MIN_COMPRESSIBLE_BYTES`, and a successful re-encode routinely drops
 * a photo below that line — so without this record the next scan silently removes exactly the photos
 * the user just worked on, with nothing on screen to explain where they went.
 *
 * It is a **ledger, not a cache**: it stores ids, never rows. The sizes, names and URIs always come
 * from the live `MediaStore` read, so a photo deleted or replaced outside the app cannot be
 * resurrected from here — an id that no longer resolves simply matches nothing.
 *
 * Fed at the source, the same rule [CleanupLedger] states: the use case that actually wrote the new
 * bytes calls [record], not the result screen.
 */
interface CompressedPhotoLedger {

    /** Every id recorded so far. An empty set is the normal first-run answer, never an error. */
    suspend fun compressedIds(): Set<PhotoId>

    /**
     * Records one photo as re-encoded by this app. Recording the same id twice is not an error and
     * does not store it twice.
     */
    suspend fun record(id: PhotoId)
}
