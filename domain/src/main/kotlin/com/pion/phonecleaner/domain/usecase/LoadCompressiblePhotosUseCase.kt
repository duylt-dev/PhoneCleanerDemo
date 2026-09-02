package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.map
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.policy.PhotoGrouping
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.collections.immutable.ImmutableList

/**
 * The compressor's candidate list: month buckets of photos big enough that re-encoding them is a
 * saving (`docs/screens/13-photo-and-media.md` §3.2).
 *
 * The competitor offers **every** jpeg and png on the device with no filter of any kind on size,
 * dimension or prior compression (`docs/reverse-engineering/13-photo-and-media.md` §4.2), and then
 * scales a 400 px thumbnail by 0.39 — re-encoding it to something larger.
 *
 * UNKNOWN — §3.2 also asks for a minimum **edge** filter (`MIN_EDGE_PX`). It is not applied here and
 * cannot be: `Photo` carries no pixel dimensions, and the one shared `ContentResolver` query builder
 * (`:data/storage/MediaStoreQuery.kt`, `PROJECTION`) selects no `WIDTH`/`HEIGHT` column — that file
 * belongs to the storage layer, not to this cluster. Looked for, and not found: a width/height field
 * on the `Photo` shape in `docs/screens/13-photo-and-media.md` §0.2, and a dimension column in the
 * digest's `MediaStoreRepository` entry. The engine covers the same ground from the other side: a
 * re-encode that would not be smaller is skipped and reported, never written (`PhotoCompressor`).
 */
class LoadCompressiblePhotosUseCase(
    private val photos: PhotoRepository,
) {
    suspend operator fun invoke(
        minBytes: Long = MIN_COMPRESSIBLE_BYTES,
    ): AppResult<ImmutableList<PhotoGroup>> =
        photos.photos().map { rows -> PhotoGrouping.byMonth(rows.filter { it.sizeBytes >= minBytes }) }

    companion object {
        /**
         * 200 KiB. Below it a JPEG re-encode at quality 78 saves little and can grow the file, and the
         * row costs the user a decision for nothing.
         *
         * UNKNOWN — no source states a threshold: §3.2 names `MIN_COMPRESSIBLE_BYTES` without a value,
         * and the competitor has no filter at all to port. The value is this implementation's, stated
         * here as one named constant rather than a literal in the filter, and it is a parameter above
         * so a test — and a later decision — can move it without touching the call site.
         */
        const val MIN_COMPRESSIBLE_BYTES: Long = 200L * 1024L
    }
}
