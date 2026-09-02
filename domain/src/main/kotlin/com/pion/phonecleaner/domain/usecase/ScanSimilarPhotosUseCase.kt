package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.photo.SimilarScanProgress
import com.pion.phonecleaner.domain.repository.SimilarPhotoScanner
import kotlinx.coroutines.flow.Flow

/**
 * The similar-photo scan, as one call the screen makes
 * (`docs/screens/13-photo-and-media.md` §1.2, §8 item 3).
 *
 * Thin on purpose: the engine is `SimilarPhotoScanner` and the rule that makes two photos similar
 * lives in its implementation, where a fixture-hash test can reach it (§1.4). What this buys is that
 * `SimilarPhotosViewModel` names one collaborator instead of three.
 */
class ScanSimilarPhotosUseCase(
    private val scanner: SimilarPhotoScanner,
) {
    operator fun invoke(): Flow<SimilarScanProgress> = scanner.scan()
}
