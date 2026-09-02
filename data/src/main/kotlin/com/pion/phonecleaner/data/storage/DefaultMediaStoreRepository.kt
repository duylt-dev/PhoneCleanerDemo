package com.pion.phonecleaner.data.storage

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.MediaStoreRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

/**
 * Strategy 3 of `docs/system-architecture.md` §8.3 and the second of the three primitives of §4.5 —
 * and the reason dropping `MANAGE_EXTERNAL_STORAGE` costs the app almost nothing on media: every
 * media-shaped feature (photos, video, audio, big media files, duplicates over media, the media half
 * of WhatsApp) is built on this, with `READ_MEDIA_*` on API 33+ and `READ_EXTERNAL_STORAGE` below.
 *
 * It replaces `nd.c` + `nd.d` + `nd.h`, three query sites for one job.
 *
 * **Never an empty list to mean failure** (§7.6). `cd.d.e` swallows a `SecurityException` into an
 * empty map, so a missing permission renders as "nothing found" and the user is told nothing. A null
 * cursor here is `AppError.PermissionDenied`; a real query that matched nothing is `Success(empty)`.
 */
internal class DefaultMediaStoreRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : MediaStoreRepository {

    override suspend fun images(): AppResult<ImmutableList<ScannedFile>> =
        collection(MediaStoreQuery.imagesUri())

    override suspend fun videos(): AppResult<ImmutableList<ScannedFile>> =
        collection(MediaStoreQuery.videosUri())

    override suspend fun audio(): AppResult<ImmutableList<ScannedFile>> =
        collection(MediaStoreQuery.audioUri())

    private suspend fun collection(uri: Uri): AppResult<ImmutableList<ScannedFile>> =
        withContext(dispatchers.io) {
            try {
                val rows = MediaStoreQuery.query(
                    context = context,
                    collection = uri,
                    sortOrder = NEWEST_FIRST,
                ) { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(cursor.toScannedFile(uri))
                    }
                }
                when (rows) {
                    // A null cursor is the provider refusing, not an empty collection.
                    null -> AppError.PermissionDenied(uri.toString()).asFailure()
                    else -> rows.toImmutableList().asSuccess()
                }
            } catch (e: SecurityException) {
                AppError.PermissionDenied(e.message ?: uri.toString()).asFailure()
            } catch (e: IllegalArgumentException) {
                // A column the OEM's provider does not expose. Reported, never silently empty.
                AppError.Storage(path = uri.toString(), cause = e.message).asFailure()
            }
        }

    private companion object {
        const val NEWEST_FIRST = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
    }
}
