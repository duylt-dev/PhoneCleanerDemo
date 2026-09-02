package com.pion.phonecleaner.data.photo

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.map
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.PhotoRepository

/**
 * The rows behind a list of ids, **in the caller's order**, dropping ids that no longer resolve.
 *
 * Both engines that take ids need this, and neither may re-open a cursor of its own: the one query
 * builder rule (`docs/system-architecture.md` §10.3 U7) is the whole reason `Photo` and `ScannedFile`
 * are allowed to be different types.
 *
 * A row that has been deleted since the picker ran is simply absent — the competitor's compressor
 * carries the path forward and throws `IllegalStateException` out of `BitmapFactory.decodeFile`
 * returning null (`docs/reverse-engineering/13-photo-and-media.md` §4.3).
 */
internal suspend fun PhotoRepository.rowsFor(ids: List<PhotoId>): AppResult<List<Photo>> =
    photos().map { all ->
        val byId = all.associateBy { it.id }
        ids.mapNotNull(byId::get)
    }
