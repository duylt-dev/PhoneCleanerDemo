package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.photo.BlurScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * The blurry-photo engine.
 *
 * It **composes** the shared primitives rather than walking storage itself
 * (`docs/system-architecture.md` §4.5): the rows come from [PhotoRepository], the sharpness figure
 * from [BlurDetector], the tier from `BlurPolicy`, and the delete — when one happens — from the one
 * `FileDeleter` behind `PhotoRepository.delete`.
 *
 * Cancellation is the collector's: the flow is collected inside the ViewModel's `scanJob`, so
 * cancelling that job stops the scoring children with it.
 */
interface BlurryPhotoScanner {
    fun scan(): Flow<BlurScanProgress>
}
