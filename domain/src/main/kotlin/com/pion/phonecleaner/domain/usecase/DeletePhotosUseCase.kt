package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.PhotoRepository

/**
 * **The one delete path in the photo cluster** — the similar grid (§1) and the album detail (§7) call
 * this same use case over the same `FileDeleter` (`docs/screens/13-photo-and-media.md` §7.2).
 *
 * Every arm of [DeleteOutcome] reaches the caller, including
 * [DeleteOutcome.PendingConsent] — which on API 30+ is the ordinary path, not an error
 * (`docs/system-architecture.md` §8.4).
 */
class DeletePhotosUseCase(
    private val photos: PhotoRepository,
) {
    suspend operator fun invoke(ids: List<PhotoId>): AppResult<DeleteOutcome> = photos.delete(ids)
}
