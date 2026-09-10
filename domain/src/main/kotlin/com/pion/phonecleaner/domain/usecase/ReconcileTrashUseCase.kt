package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.repository.TrashRepository

/**
 * Settles rows a process kill left `PENDING` — the T7 sweep (see `plan.md`'s risk table).
 *
 * Called by the trash screen on start (Phase 05) and, unconditionally, by [PurgeExpiredTrashUseCase]
 * before it purges (Phase 06's worker). Safe to call repeatedly and from both places at once: it is a
 * settle over what the filesystem actually shows, not a counter.
 */
class ReconcileTrashUseCase(
    private val trash: TrashRepository,
) {
    suspend operator fun invoke(): AppResult<Int> = trash.reconcile()
}
