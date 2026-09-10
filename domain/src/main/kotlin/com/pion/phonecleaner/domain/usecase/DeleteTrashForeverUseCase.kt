package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.trash.TrashPurgeOutcome
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Deletes selected rows forever, and **records what that freed**.
 * Once started, the confirmed batch and its accounting settle even if the caller leaves.
 * Cancelling between deletion and the ledger write would permanently lose the byte credit.
 * This is cancellation-safe, not an atomic transaction across Room and DataStore on process death.
 *
 * **This is one of only two places in the app that credit trashed bytes to [CleanupLedger]** — the
 * other is [PurgeExpiredTrashUseCase]. The move into the bin credits nothing, because nothing was
 * freed at that point: the bytes are still on the volume, in the bin
 * (`TrashMoveOutcome.movedBytes`'s own KDoc). This is the moment they actually leave the device.
 */
class DeleteTrashForeverUseCase(
    private val trash: TrashRepository,
    private val ledger: CleanupLedger,
) {

    suspend operator fun invoke(ids: List<String>): AppResult<TrashPurgeOutcome> {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) { record(trash.deleteForever(ids)) }
    }

    /** The user's Empty All confirmation applies to the bin, not just its first displayed page. */
    suspend fun all(): AppResult<TrashPurgeOutcome> {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) { record(trash.deleteAllForever()) }
    }

    private suspend fun record(result: AppResult<TrashPurgeOutcome>): AppResult<TrashPurgeOutcome> {
        val outcome = (result as? AppResult.Success)?.value
        if (outcome != null && outcome.freedBytes > 0L) {
            ledger.record(outcome.freedBytes)
        }
        return result
    }
}
