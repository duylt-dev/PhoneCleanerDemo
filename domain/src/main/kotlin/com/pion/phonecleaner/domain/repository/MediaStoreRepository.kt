package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.ScannedFile
import kotlinx.collections.immutable.ImmutableList

/**
 * Primitive 2 of 3 — the three media collections. Replaces `nd.c` + `nd.d` + `nd.h`; declared once in
 * `storageDataModule` (`docs/system-architecture.md` §4.5, §5.6).
 *
 * Every row carries `FileOrigin.MediaStoreEntry(contentUri)` **resolved at scan time**, so a later
 * delete can never hit the wrong row.
 *
 * This is strategy 3 of the four access strategies and is the default branch's main surface:
 * `READ_MEDIA_IMAGES`/`_VIDEO`/`_AUDIO` on API 33+, `READ_EXTERNAL_STORAGE` below, and Android 14
 * partial access is a normal outcome, not a failure (`docs/system-architecture.md` §8.3).
 */
interface MediaStoreRepository {
    suspend fun images(): AppResult<ImmutableList<ScannedFile>>
    suspend fun videos(): AppResult<ImmutableList<ScannedFile>>
    suspend fun audio(): AppResult<ImmutableList<ScannedFile>>
}
