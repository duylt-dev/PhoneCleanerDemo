package com.pion.phonecleaner.data.photo

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.data.storage.MediaStoreQuery
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoAlbum
import com.pion.phonecleaner.domain.model.photo.PhotoId
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The photo cluster's read surface over `MediaStore.Images`
 * (`docs/screens/13-photo-and-media.md` §0.1, §0.3).
 *
 * **`MANAGE_EXTERNAL_STORAGE` is never asked for here.** This is strategy 3 of
 * `docs/system-architecture.md` §8.3 — `READ_MEDIA_IMAGES` on API 33+, `READ_EXTERNAL_STORAGE` below
 * — and Android 14's partial media grant is a normal outcome, not a failure: fewer rows come back and
 * nothing pretends otherwise. The competitor demands all-files access for three of these four tools.
 *
 * The dispatcher choice is made **inside** the repository (`LLM.md` §6.5): a caller never picks one,
 * which is how `cd.d.d` ends up behaving differently on each screen that uses it.
 */
internal class MediaStorePhotoRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val deleter: FileDeleter,
) : PhotoRepository {

    override suspend fun photos(): AppResult<ImmutableList<Photo>> =
        withContext(dispatchers.io) { queryPhotos(IMAGE_MIME_SELECTION, IMAGE_MIME_ARGS) }

    override suspend fun photosInFolder(folderName: String): AppResult<ImmutableList<Photo>> =
        withContext(dispatchers.io) {
            // Unfiltered by MIME: an album that hides a HEIC photo is wrong about the device.
            // The folder match is in memory because the path column is RELATIVE_PATH on API 29+ and
            // DATA below it, and a SQL `LIKE` would be a second copy of a branch `MediaStoreQuery`
            // has already resolved once.
            when (val all = queryPhotos(selection = null, args = null)) {
                is AppResult.Failure -> all
                is AppResult.Success ->
                    all.value.filter { it.folderName == folderName }.toImmutableList().asSuccess()
            }
        }

    /**
     * A feed, not a one-shot: a photo added or deleted elsewhere updates the album screen without a
     * re-entry (§6.2). The competitor queries once in `onCreate` and never again.
     *
     * The observer only *signals*; the re-read runs on this flow's own coroutine, so no `MediaStore`
     * query ever happens on the callback's looper thread.
     */
    override fun observeAlbums(): Flow<AppResult<ImmutableList<PhotoAlbum>>> = callbackFlow {
        val signals = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                signals.trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(MediaStoreQuery.imagesUri(), true, observer)
        send(albums())
        val pump = launch {
            for (signal in signals) send(albums())
        }
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
            signals.close()
            pump.cancel()
        }
    }.flowOn(dispatchers.io)

    /**
     * Maps to `ScannedFile` via [resolve] and delegates to the one [FileDeleter].
     * [DeleteOutcome.PendingConsent] comes back untouched: on API 30+ the system dialog is the
     * ordinary path, and only an Activity can launch its `IntentSender`
     * (`docs/system-architecture.md` §8.4).
     */
    override suspend fun delete(ids: List<PhotoId>): AppResult<DeleteOutcome> {
        if (ids.isEmpty()) return DeleteOutcome.NothingResolved.asSuccess()
        return when (val resolved = resolve(ids)) {
            is AppResult.Failure -> resolved
            is AppResult.Success -> deleter.delete(resolved.value)
        }
    }

    /**
     * The pure `Photo -> ScannedFile` projection (plan `260908-0801-trash-bin`, Phase 07 step 4).
     * [delete] calls this, so there remains exactly **one** projection out of this class — the same
     * promise `PhotoRepository.kt`'s own KDoc states.
     */
    override suspend fun resolve(ids: List<PhotoId>): AppResult<ImmutableList<ScannedFile>> {
        val wanted = ids.toSet()
        val rows = withContext(dispatchers.io) { queryPhotos(selection = null, args = null) }
        return when (rows) {
            is AppResult.Failure -> rows
            is AppResult.Success ->
                rows.value.filter { it.id in wanted }.map { it.toScannedFile() }.toImmutableList().asSuccess()
        }
    }

    private fun albums(): AppResult<ImmutableList<PhotoAlbum>> =
        when (val all = queryPhotos(selection = null, args = null)) {
            is AppResult.Failure -> all
            is AppResult.Success -> all.value.foldToAlbums().asSuccess()
        }

    /**
     * A null cursor is **not** "no rows": `cd.d.e` swallows a `SecurityException` into an empty map,
     * so a permission failure renders as "nothing found" (`MediaStoreQuery.query`'s own note).
     */
    private fun queryPhotos(selection: String?, args: Array<String>?): AppResult<ImmutableList<Photo>> =
        try {
            val collection = MediaStoreQuery.imagesUri()
            val rows = MediaStoreQuery.query(
                context = context,
                collection = collection,
                selection = selection,
                selectionArgs = args,
                sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC",
            ) { cursor ->
                buildList {
                    while (cursor.moveToNext()) cursor.toPhoto(collection)?.let(::add)
                }
            }
            rows?.toImmutableList()?.asSuccess() ?: AppError.Storage(cause = NO_CURSOR).asFailure()
        } catch (security: SecurityException) {
            AppError.PermissionDenied(security.message).asFailure()
        }

    private companion object {
        const val NO_CURSOR = "MediaStore returned no cursor for the image collection"
    }
}
