package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.domain.model.trash.TrashEntry
import com.pion.phonecleaner.domain.repository.TrashRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The bin's contents, straight from [TrashRepository.observeEntries].
 *
 * A pass-through, on purpose: it exists so the trash screen's ViewModel names a use case, never a
 * repository directly (`LLM.md` §6.5, constructor injection only).
 */
class ObserveTrashUseCase(
    private val trash: TrashRepository,
) {
    operator fun invoke(): Flow<ImmutableList<TrashEntry>> = trash.observeEntries()
}
