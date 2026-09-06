package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.photo.BlurScanProgress
import com.pion.phonecleaner.domain.repository.BlurryPhotoScanner
import kotlinx.coroutines.flow.Flow

/**
 * The blurry-photo scan, as one call the screen makes.
 *
 * Thin on purpose, exactly like `ScanSimilarPhotosUseCase`: the engine is `BlurryPhotoScanner` and
 * the rule that makes a photo blurry lives in `BlurPolicy`, where a plain-JVM test reaches it. What
 * this buys is that `BlurryPhotosViewModel` names one collaborator instead of three.
 */
class ScanBlurryPhotosUseCase(
    private val scanner: BlurryPhotoScanner,
) {
    operator fun invoke(): Flow<BlurScanProgress> = scanner.scan()
}
