package com.pion.phonecleaner.data.photo

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.photo.BlurScanProgress
import com.pion.phonecleaner.domain.model.photo.BlurTier
import com.pion.phonecleaner.domain.model.photo.Photo
import com.pion.phonecleaner.domain.model.photo.PhotoGroup
import com.pion.phonecleaner.domain.policy.BlurPolicy
import com.pion.phonecleaner.domain.repository.BlurDetector
import com.pion.phonecleaner.domain.repository.BlurryPhotoScanner
import com.pion.phonecleaner.domain.repository.PhotoRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The blurry-photo engine.
 *
 * It **composes** the shared primitives: rows from `PhotoRepository`, sharpness from `BlurDetector`,
 * the tier from `BlurPolicy`. No walk of its own exists here (`docs/system-architecture.md` §4.5).
 *
 * ### What it does NOT do
 *
 * **It does not write the score onto `Photo`.** A `blurScore: Double?` field would ride on every row
 * of a 5 000-item list to serve one screen, which is the cost `LLM.md` §12 refuses when it keeps
 * `ScannedFile` and `Photo` apart. The score is used here — to pick a tier and to order within it —
 * and then dropped. The tier survives as the group the row lands in, which is all the screen reads.
 *
 * **It does not report the raw figure to the UI.** See `BlurTier`.
 */
internal class DefaultBlurryPhotoScanner(
    private val photos: PhotoRepository,
    private val detector: BlurDetector,
    private val dispatchers: DispatcherProvider,
) : BlurryPhotoScanner {

    override fun scan(): Flow<BlurScanProgress> = flow {
        val rows = when (val result = photos.photos()) {
            is AppResult.Failure -> {
                emit(BlurScanProgress.Failed(result.error))
                return@flow
            }

            is AppResult.Success -> result.value
        }
        emit(BlurScanProgress.Scoring(done = 0, total = rows.size))
        if (rows.isEmpty()) {
            emit(BlurScanProgress.Done(persistentListOf()))
            return@flow
        }

        // The budget for the whole scan, held as a Semaphore rather than as a
        // `limitedParallelism` dispatcher. A limited dispatcher bounds *dispatch slots*, and a slot
        // is released at the first suspension inside the child — so one `withContext` in the
        // detector would silently restore unbounded concurrency. A permit is held across the whole
        // measurement, which is the thing that actually costs memory: a decoded bitmap, its 512 px
        // copy and an IntArray, roughly 4.6 MB in flight per photo. Unbounded, a 16-core device
        // would hold ~74 MB, which is an OOM on a 2 GB phone.
        val budget = Semaphore(SCORE_PARALLELISM)
        val scored = ArrayList<Pair<Photo, Double>>(rows.size)
        var done = 0
        var skipped = 0
        for (batch in rows.chunked(PROGRESS_BATCH)) {
            // The children are structural children of THIS collector's coroutine: cancelling the
            // ViewModel's scanJob stops the scoring with it, and nothing is cancelled by hand.
            val results = coroutineScope {
                batch.map { photo -> async { photo to scoreOrNull(budget, photo) } }.awaitAll()
            }
            for ((photo, score) in results) {
                done++
                // A photo that could not be measured is NOT scored zero — zero is the blurriest
                // possible reading, and on this screen it would pre-tick the row for deletion.
                if (score == null) skipped++ else scored += photo to score
            }
            emit(BlurScanProgress.Scoring(done = done, total = rows.size))
        }
        emit(BlurScanProgress.Done(groups = groupsOf(scored), skipped = skipped))
    }.flowOn(dispatchers.default)

    /**
     * One photo's score, or `null` if it could not be measured **for any reason at all**.
     *
     * `BlurDetector.score` already answers `null` for the failures it expects, but it decodes and
     * allocates, so it can still throw — an `OutOfMemoryError` on a hostile file, a driver-level
     * failure inside `getPixels`. Left uncaught, one such photo takes down the whole scan through
     * `awaitAll`, and a 5 000-photo library ends as `AppError.Unexpected` with nothing to show. The
     * design says a per-photo failure is one `skipped` count (`BlurScanProgress.Done.skipped`), so
     * it is contained here to match.
     *
     * `CancellationException` is rethrown: it is how this coroutine is told to stop, and swallowing
     * it would make the ViewModel's `scanJob.cancel()` do nothing.
     */
    private suspend fun scoreOrNull(budget: Semaphore, photo: Photo): Double? = try {
        budget.withPermit { detector.score(photo.contentUri) }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        null
    }

    /**
     * One group per tier that found members, in `BlurTier` declaration order, blurriest row first
     * within each.
     *
     * `key` is the tier's own name and `label` repeats it: `PhotoGroup.label` is contractually a
     * machine-shaped string because `:domain` has no `Resources` and must not format user copy, so
     * the screen maps this key to a `stringResource` at render. An empty tier produces **no group**
     * rather than an empty one — a header with nothing under it is a header that lies.
     */
    private fun groupsOf(scored: List<Pair<Photo, Double>>): ImmutableList<PhotoGroup> {
        val byTier = scored.groupBy { (_, score) -> BlurPolicy.tierOf(score) }
        return BlurTier.entries.mapNotNull { tier ->
            val members = byTier[tier].orEmpty()
            if (members.isEmpty()) return@mapNotNull null
            PhotoGroup(
                key = tier.name,
                label = tier.name,
                // Blurriest first: the rows a reader who scrolls no further should see.
                photos = members.sortedBy { (_, score) -> score }.map { (photo, _) -> photo }.toImmutableList(),
            )
        }.toImmutableList()
    }

    private companion object {
        /** Four measurements at once. Enough to keep the cores busy, few enough to survive a
         *  large library on a small-heap device — see the Semaphore comment in [scan]. */
        const val SCORE_PARALLELISM = 4

        /** How often the screen's counter moves. One emission per photo would be 5 000 emissions. */
        const val PROGRESS_BATCH = 16
    }
}
