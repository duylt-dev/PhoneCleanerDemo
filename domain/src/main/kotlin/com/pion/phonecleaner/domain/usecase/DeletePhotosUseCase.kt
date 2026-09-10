package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.map
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.model.trash.asDeleteOutcome
import com.pion.phonecleaner.domain.repository.PhotoRepository
import com.pion.phonecleaner.domain.repository.TrashRepository

/**
 * **The one delete path in the photo cluster** — the similar grid, the blurry grid and the album
 * detail screen all call this same use case (`docs/screens/13-photo-and-media.md` §7.2).
 *
 * Into the bin when one exists, permanently when it does not (plan `260908-0801-trash-bin`, Phase 07
 * key insight 4). The branch has to live here, in `:domain`, rather than inside
 * [PhotoRepository.delete]: hiding it in `:data` is exactly what engineer decision E3 forbids, because
 * it would make the exception — the trash branch — invisible at the call site. [PhotoRepository]
 * therefore exposes a pure projection, [PhotoRepository.resolve], with no policy attached, and this
 * use case is what decides where the resolved [com.pion.phonecleaner.domain.model.file.ScannedFile]s
 * go.
 *
 * A photo's absolute path for the mover comes from `MediaStoreQuery.absolutePathFor`, never from
 * `ScannedFile.path` — on API 29+ that column is `RELATIVE_PATH`, a folder, not a file. That
 * resolution is internal to `TrashRepository`'s implementation and invisible here; it is stated so
 * nobody "simplifies" the mover later.
 *
 * Every arm of [DeleteOutcome] reaches the caller, including [DeleteOutcome.PendingConsent] — which
 * on API 30+ is the ordinary path on the no-trash branch, not an error
 * (`docs/system-architecture.md` §8.4), and which **cannot happen** on the trash branch: no delete
 * request is ever built there.
 */
class DeletePhotosUseCase(
    private val photos: PhotoRepository,
    private val trash: TrashRepository,
) {
    /** Executes the confirmed mode. A refused trash move never authorizes permanent deletion. */
    suspend operator fun invoke(ids: List<PhotoId>, source: FeatureId, requireTrash: Boolean): AppResult<DeleteOutcome> {
        if (!requireTrash) return photos.delete(ids)
        return when (val files = photos.resolve(ids)) {
            is AppResult.Failure -> files
            is AppResult.Success -> trash.trashFiles(files.value, source).map { it.asDeleteOutcome() }
        }
    }
}
