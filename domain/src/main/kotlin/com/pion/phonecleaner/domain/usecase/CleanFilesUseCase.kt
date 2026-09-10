package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileCleanProgress
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * A batched delete that **reports progress as it goes**, into the bin when one exists and
 * permanently when it does not (plan `260908-0801-trash-bin`, Phase 07 step 3).
 *
 * The WhatsApp cleaner's live readout is the reason this exists as a `Flow` rather than one
 * `DeleteFilesUseCase` call: the competitor posts its remaining size per file and its observer
 * discards the payload, so the number never moves — a progress readout that does not progress is
 * worse than none. Batching keeps the consent round trip whole: a batch either needs the system
 * dialog or does not.
 *
 * **The ledger write is in a `finally`, under `NonCancellable`, and is skipped whenever the run was
 * recoverable.** A clean the user stops mid-way has still freed what it freed on the no-trash branch,
 * and a `finally` reached by cancellation throws at its first suspension point without it — this is
 * still load-bearing there. On the trash branch nothing is freed at move time, so the ledger is never
 * written here regardless of how the run ends; the bytes are credited once, later, when they actually
 * leave the device ([DeleteTrashForeverUseCase] / [PurgeExpiredTrashUseCase]). The competitor's
 * `onDestroy` cancels its loop with no journal at all: the files stay deleted and the byte count is
 * lost, so its bookkeeping ends up wrong in the direction that flatters it.
 *
 * A batch the bin could not move at all is reported through [FileCleanProgress.Failed] and never
 * retried against [deleter] — those paths are untouched, still on the device, with no trash row.
 */
class CleanFilesUseCase(
    private val deleter: FileDeleter,
    private val trash: TrashRepository,
    private val ledger: CleanupLedger,
) {

    /** Executes the confirmed mode for every batch, even if access changes mid-run. */
    operator fun invoke(files: List<ScannedFile>, source: FeatureId, requireTrash: Boolean): Flow<FileCleanProgress> = flow {
        var freed = 0L
        var deleted = 0
        var failed = 0
        var recoverable = false
        try {
            if (requireTrash) {
                recoverable = true
                for (batch in files.chunked(BATCH_SIZE)) {
                    when (val moved = trash.trashFiles(batch, source)) {
                        is AppResult.Success -> {
                            val outcome = moved.value
                            freed += outcome.movedBytes
                            deleted += outcome.movedIds.size
                            failed += outcome.failedPaths.size
                            emit(FileCleanProgress.Deleted(outcome.movedIds, outcome.movedBytes))
                            if (outcome.failedPaths.isNotEmpty()) {
                                emit(FileCleanProgress.Failed(outcome.failedPaths))
                            }
                        }

                        // The bin itself refused the whole batch (e.g. access revoked mid-loop):
                        // reported, and never a reason to fall back to permanently deleting it.
                        is AppResult.Failure -> {
                            failed += batch.size
                            emit(FileCleanProgress.Failed(batch.map(ScannedFile::path).toImmutableList()))
                        }
                    }
                }
                // NeedsConsent is unreachable here: FileDeleter is never called on the trash path, so
                // no delete request is ever built.
            } else {
                for (batch in files.chunked(BATCH_SIZE)) {
                    when (val outcome = (deleter.delete(batch) as? AppResult.Success)?.value) {
                        is DeleteOutcome.Deleted -> {
                            freed += outcome.freedBytes
                            deleted += outcome.ids.size
                            failed += outcome.failedPaths.size
                            emit(FileCleanProgress.Deleted(outcome.ids, outcome.freedBytes))
                            if (outcome.failedPaths.isNotEmpty()) {
                                emit(FileCleanProgress.Failed(outcome.failedPaths))
                            }
                        }

                        // Only an Activity can launch the IntentSender, so the flow stops here and the
                        // screen re-issues the clean for what is left after the round trip.
                        is DeleteOutcome.PendingConsent -> {
                            emit(FileCleanProgress.NeedsConsent(outcome.request, outcome.ids))
                            return@flow
                        }

                        // A first-class outcome, not silence: the competitor's video delete returns
                        // without invoking its callback when nothing resolves, stranding its busy flag.
                        DeleteOutcome.NothingResolved, null -> {
                            failed += batch.size
                            emit(FileCleanProgress.Failed(batch.map(ScannedFile::path).toImmutableList()))
                        }
                    }
                }
            }
            emit(FileCleanProgress.Finished(freed, deleted, failed, recoverable))
        } finally {
            withContext(NonCancellable) { if (!recoverable && freed > 0L) ledger.record(freed) }
        }
    }

    private companion object {
        /**
         * UNKNOWN — no source states a batch size. Small enough that the readout moves several times
         * on an ordinary bucket, large enough that one system consent dialog covers many rows rather
         * than one per file, which is the failure mode §5.5 records for stacked dialogs.
         */
        const val BATCH_SIZE = 24
    }
}
