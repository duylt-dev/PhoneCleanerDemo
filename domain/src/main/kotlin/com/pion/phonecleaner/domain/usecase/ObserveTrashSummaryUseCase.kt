package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.trash.TrashSummary
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.coroutines.flow.Flow

/**
 * The count and the byte total, for the Settings row and the home tile badge — without loading the
 * full list (see `TrashSummary`'s own KDoc).
 */
class ObserveTrashSummaryUseCase(
    private val trash: TrashRepository,
) {
    operator fun invoke(): Flow<TrashSummary> = trash.observeSummary()
}
