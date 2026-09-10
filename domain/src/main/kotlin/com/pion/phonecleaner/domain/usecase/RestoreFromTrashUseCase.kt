package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.domain.repository.TrashRepository

/**
 * Puts rows back at their original path.
 *
 * **No ledger write.** The move into the bin never credited anything to `CleanupLedger` — a trashed
 * file has not freed a byte (the plan's own opening sentence) — so the reverse of that move has
 * nothing to un-credit either. Only [DeleteTrashForeverUseCase] and [PurgeExpiredTrashUseCase] touch
 * the ledger; this is deliberately not a third place.
 */
class RestoreFromTrashUseCase(
    private val trash: TrashRepository,
) {
    suspend operator fun invoke(ids: List<String>): AppResult<TrashRestoreOutcome> = trash.restore(ids)
}
