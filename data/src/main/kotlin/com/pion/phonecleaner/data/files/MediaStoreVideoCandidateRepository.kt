package com.pion.phonecleaner.data.files

import android.content.Context
import android.provider.MediaStore
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.data.storage.MediaStoreQuery
import com.pion.phonecleaner.data.storage.toVideoCandidate
import com.pion.phonecleaner.domain.model.video.VideoCandidate
import com.pion.phonecleaner.domain.repository.VideoCandidateRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.withContext

/**
 * The compressor's read surface over `MediaStore.Video`
 * (`phase-04-data-media3-engine.md` step 2).
 *
 * **Not** [com.pion.phonecleaner.domain.repository.MediaStoreRepository.videos] widened — that port
 * is shared by six file tools and returns `ScannedFile`; a video candidate needs duration and pixel
 * dimensions those tools do not. Both projections come out of the **one** query builder,
 * `MediaStoreQuery`, extended with [MediaStoreQuery.VIDEO_PROJECTION] rather than opened as a second
 * cursor (`LLM.md` §12's mandatory mitigation).
 *
 * **A null cursor is `AppError.PermissionDenied`, never an empty list.** The same rule
 * `DefaultMediaStoreRepository` states: `cd.d.e` swallows a `SecurityException` into an empty map, so
 * a permission failure renders as "nothing found" (`LLM.md` §7.6).
 */
internal class MediaStoreVideoCandidateRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : VideoCandidateRepository {

    override suspend fun candidates(): AppResult<ImmutableList<VideoCandidate>> =
        withContext(dispatchers.io) { queryVideos() }

    /**
     * The rows for [ids], **in the order given** — the user's order, not the collection's. A video
     * absent from the current query (deleted, revoked grant) is simply left out rather than failing
     * the whole batch.
     */
    override suspend fun rowsFor(ids: List<String>): AppResult<ImmutableList<VideoCandidate>> =
        withContext(dispatchers.io) {
            when (val all = queryVideos()) {
                is AppResult.Failure -> all
                is AppResult.Success -> {
                    val byId = all.value.associateBy(VideoCandidate::id)
                    ids.mapNotNull(byId::get).toImmutableList().asSuccess()
                }
            }
        }

    private fun queryVideos(): AppResult<ImmutableList<VideoCandidate>> = try {
        val collection = MediaStoreQuery.videosUri()
        val rows = MediaStoreQuery.query(
            context = context,
            collection = collection,
            projection = MediaStoreQuery.VIDEO_PROJECTION,
            sortOrder = NEWEST_FIRST,
        ) { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toVideoCandidate(collection))
            }
        }
        when (rows) {
            // A null cursor is the provider refusing, not an empty collection (LLM.md §7.6).
            null -> AppError.PermissionDenied(collection.toString()).asFailure()
            else -> rows.toImmutableList().asSuccess()
        }
    } catch (security: SecurityException) {
        log.e(security) { "No access to the video collection" }
        AppError.PermissionDenied(security.message).asFailure()
    } catch (illegalArgument: IllegalArgumentException) {
        // An OEM provider that does not expose one of VIDEO_PROJECTION's columns. Reported, never
        // silently empty (the same guard DefaultMediaStoreRepository applies).
        log.e(illegalArgument) { "Video collection query rejected the projection" }
        AppError.Storage(path = null, cause = illegalArgument.message).asFailure()
    }

    private companion object {
        const val NEWEST_FIRST = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
    }
}
