package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.junk.CleanOutcome
import com.pion.phonecleaner.domain.model.junk.CleanProgress
import com.pion.phonecleaner.domain.model.trash.TrashDirectoryRequest
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.JunkRepository
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * Deletes what the review screen selected — into the bin when one exists, permanently when it does
 * not (plan `260908-0801-trash-bin`, Phase 07 step 5) — and **persists what that actually freed**.
 *
 * **T3/T4, and why this never calls [com.pion.phonecleaner.domain.repository.JunkDeleter] on the
 * trash branch.** `JunkPathDelete` is the only directory-removing path in the app, and owner decision
 * D6 says a tree is ONE entry, so the trash branch calls [TrashRepository.trashDirectory] once per
 * selected path and emits [CleanProgress.Deleted] per path — the same per-path shape
 * `JunkCleanViewModel` already reduces, so it needs no new arm. A `content://` junk path is not
 * absolute, so [TrashRepository.trashDirectory] refuses it into `failedPaths` without touching
 * anything, which lands here as [CleanProgress.Failed] — nothing deleted, exactly the outcome the
 * no-trash branch's own documented gap already produces for that path.
 *
 * A directory [TrashRepository.trashDirectory] could not move at all stays exactly where it was: it
 * is reported through [CleanProgress.Failed] and is never retried against the real deleter.
 *
 * The ViewModel orchestrates; it does not persist (`docs/screens/12-junk-cleaning.md` §5.2, MVI §3
 * rule 7). Two writes belong here and nowhere else:
 *
 *  * [CleanupLedger.record] — a raw `Long`, at the source, and **only on the no-trash branch**: a
 *    move frees nothing, so the trash branch never calls it. The bytes are credited exactly once,
 *    later, in [DeleteTrashForeverUseCase] / [PurgeExpiredTrashUseCase], when they actually leave the
 *    device.
 *  * [JunkRepository.invalidateEstimate] — so the Home badge shows "not measured" until something
 *    measures again. This runs on **both** branches: a path is gone from where it was either way, so
 *    the estimate is stale either way. The competitor writes `flux_sp_key_home_cleaner_size = 0`
 *    unconditionally after its loop, so the badge reports "0 junk" even when every delete failed.
 *
 * **Both writes run in a `finally`, under `NonCancellable`**, so a clean cancelled mid-loop still
 * records what was actually freed and still invalidates. The competitor's `onDestroy` cancels the
 * loop with no journal: the files stay deleted, `cleanedSize` is lost, and the caches are never reset
 * — its bookkeeping ends up wrong in the direction that flatters it (Delta C10).
 *
 * Registered as `factoryOf(::CleanJunkUseCase)` in `domainModule` — a use case is never a `single`
 * (`LLM.md` §6.3).
 */
class CleanJunkUseCase(
    private val repository: JunkRepository,
    private val trash: TrashRepository,
    private val ledger: CleanupLedger,
) {

    operator fun invoke(paths: Set<String>, requireTrash: Boolean): Flow<CleanProgress> = flow {
        var freed = 0L
        var recoverable = false
        try {
            if (requireTrash) {
                recoverable = true
                var deletedCount = 0
                val failedPaths = mutableListOf<String>()
                for (path in paths) {
                    currentCoroutineContext().ensureActive()
                    when (val moved = trash.trashDirectory(TrashDirectoryRequest(path, FeatureId.JunkClean))) {
                        is AppResult.Success -> {
                            val outcome = moved.value
                            if (outcome.failedPaths.isEmpty()) {
                                freed += outcome.movedBytes
                                deletedCount += 1
                                emit(CleanProgress.Deleted(path, outcome.movedBytes))
                            } else {
                                failedPaths += path
                                emit(CleanProgress.Failed(path, AppError.Storage(path = path)))
                            }
                        }

                        is AppResult.Failure -> {
                            failedPaths += path
                            emit(CleanProgress.Failed(path, moved.error))
                        }
                    }
                }
                emit(
                    CleanProgress.Finished(
                        CleanOutcome(
                            freedBytes = freed,
                            deletedCount = deletedCount,
                            failedPaths = failedPaths.toImmutableList(),
                            recoverable = true,
                        ),
                    ),
                )
            } else {
                repository.clean(paths).collect { progress ->
                    if (progress is CleanProgress.Deleted) freed += progress.freedBytes
                    emit(progress)
                }
            }
        } finally {
            // `withContext(NonCancellable)` is load-bearing, not defensive: a `finally` reached by
            // cancellation runs inside an already-cancelled job, where the very first suspension
            // point throws again — so without it the two writes below are exactly the journal the
            // competitor also fails to keep, just spelled in Kotlin.
            withContext(NonCancellable) {
                // Only real frees reach the lifetime counter. A move frees nothing.
                if (!recoverable) ledger.record(freed)
                // ALWAYS: a path is gone from where it was either way, so the Home badge must
                // re-measure. The competitor writes `home_cleaner_size = 0` unconditionally instead.
                repository.invalidateEstimate()
            }
        }
    }
}
