package com.pion.phonecleaner.data.photo

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.model.photo.SimilarScanProgress
import com.pion.phonecleaner.domain.policy.PhotoGrouping
import com.pion.phonecleaner.domain.repository.PerceptualHasher
import com.pion.phonecleaner.domain.repository.PhotoRepository
import com.pion.phonecleaner.domain.repository.SimilarPhotoScanner
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * The similar-photo engine (`docs/screens/13-photo-and-media.md` §1.2, §1.4).
 *
 * It **composes** the shared primitives: rows from `PhotoRepository`, hashes from `PerceptualHasher`.
 * No walk of its own exists here (`docs/system-architecture.md` §4.5).
 *
 * ### Two rules, decided here and stated, because §8 item 1 says they are a product decision
 *
 * **Grouping is opener-only**, the competitor's rule, kept: a photo is compared against the group's
 * opener and never re-tested once assigned, so a group can hold two photos further apart than the
 * threshold. §1.4 allows exactly two answers — single linkage, or "the opener rule kept **and
 * stated**" — and closing an open product decision the other way is not this cluster's to make. It is
 * one function, [groupOf], so the day it is decided the change is one place with fixture-hash tests
 * around it.
 *
 * **Bounded parallelism.** The hashing runs `limitedParallelism(HASH_PARALLELISM)` over
 * `dispatchers.default`; the competitor launches one coroutine per photo with no limit, each
 * decoding a full-resolution bitmap.
 */
internal class DefaultSimilarPhotoScanner(
    private val photos: PhotoRepository,
    private val hasher: PerceptualHasher,
    private val dispatchers: DispatcherProvider,
) : SimilarPhotoScanner {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun scan(): Flow<SimilarScanProgress> = flow {
        val rows = when (val result = photos.photos()) {
            is AppResult.Failure -> {
                emit(SimilarScanProgress.Failed(result.error))
                return@flow
            }

            is AppResult.Success -> result.value.sortedByDescending { it.takenAt }
        }
        emit(SimilarScanProgress.Hashing(done = 0, total = rows.size))
        if (rows.isEmpty()) {
            emit(SimilarScanProgress.Done(persistentListOf()))
            return@flow
        }

        val limited = dispatchers.default.limitedParallelism(HASH_PARALLELISM)
        val hashed = ArrayList<Photo>(rows.size)
        var skipped = 0
        for (batch in rows.chunked(PROGRESS_BATCH)) {
            // The children are structural children of THIS collector's coroutine: cancelling the
            // ViewModel's scanJob stops the hashing with it, and nothing has to be cancelled by hand.
            val results = withContext(limited) {
                coroutineScope { batch.map { photo -> async { photo to hasher.hash(photo.contentUri) } }.awaitAll() }
            }
            for ((photo, hash) in results) {
                if (hash == null) skipped++ else hashed += photo.copy(perceptualHash = hash)
            }
            emit(SimilarScanProgress.Hashing(done = hashed.size + skipped, total = rows.size))
        }
        emit(SimilarScanProgress.Done(groups = groupsOf(hashed), skipped = skipped))
    }.flowOn(dispatchers.default)

    private fun groupsOf(hashed: List<Photo>): ImmutableList<PhotoGroup> {
        val remaining = hashed.toMutableList()
        val groups = ArrayList<PhotoGroup>()
        while (remaining.isNotEmpty()) {
            val opener = remaining.removeAt(0)
            val members = groupOf(opener, remaining)
            remaining.removeAll(members.toSet())
            // Buckets of one are not a similarity, and the competitor discards them too.
            if (members.isEmpty()) continue
            val photos = (listOf(opener) + members).toImmutableList()
            groups += PhotoGroup(
                key = opener.id.value.toString(),
                label = PhotoGrouping.dayLabel(opener),
                photos = photos,
            )
        }
        return groups.toImmutableList()
    }

    /** Membership against the opener alone. See the class KDoc — this is the whole rule. */
    private fun groupOf(opener: Photo, candidates: List<Photo>): List<Photo> {
        val openerHash = opener.perceptualHash ?: return emptyList()
        return candidates.filter { candidate ->
            val hash = candidate.perceptualHash ?: return@filter false
            hasher.distance(openerHash, hash) < DctPerceptualHasher.SIMILARITY_MAX_DISTANCE
        }
    }

    private companion object {
        /** Four decoders at once. Enough to keep the cores busy, few enough to survive a large library. */
        const val HASH_PARALLELISM = 4

        /** How often the screen's counter moves. One emission per photo would be 5 000 emissions. */
        const val PROGRESS_BATCH = 16
    }
}
