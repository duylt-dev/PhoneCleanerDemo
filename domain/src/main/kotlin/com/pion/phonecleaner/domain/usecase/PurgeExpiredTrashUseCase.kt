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
 * The worker's (and the screen's) one call: settle interrupted rows, then purge everything expired,
 * then credit the ledger.
 *
 * **Reconciles first, on purpose.** A row a process kill left `PENDING` has to be resolved to either
 * `TRASHED` or removed before expiry can even be asked about it — `TrashRepository.purgeExpired` reads
 * `expires_at`, which a still-`PENDING` row may not have committed. [ReconcileTrashUseCase] exists
 * separately (the screen also calls it on start), but the worker must not depend on the screen having
 * run first, so this use case reconciles for itself. `reconcile()`'s own result does not gate the
 * purge below: a reconcile failure still leaves [TrashRepository.purgeExpired] free to collect
 * whatever is already `TRASHED` and expired — only a purge failure is returned to the caller.
 *
 * **This is one of only two places in the app that credit trashed bytes to [CleanupLedger]** — the
 * other is [DeleteTrashForeverUseCase]. Purge is the moment the bytes actually leave the device.
 */
class PurgeExpiredTrashUseCase(
    private val trash: TrashRepository,
    private val ledger: CleanupLedger,
) {

    suspend operator fun invoke(): AppResult<TrashPurgeOutcome> {
        trash.reconcile()
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) { purgeAndRecord() }
    }

    private suspend fun purgeAndRecord(): AppResult<TrashPurgeOutcome> {
        val result = trash.purgeExpired()
        val outcome = (result as? AppResult.Success)?.value
        if (outcome != null && outcome.freedBytes > 0L) {
            ledger.record(outcome.freedBytes)
        }
        return result
    }
}
