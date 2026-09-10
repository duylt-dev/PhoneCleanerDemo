package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.trash.TrashRestoreOutcome
import com.pion.phonecleaner.domain.repository.TrashRepository

class RestoreZipFromTrashUseCase(private val trash: TrashRepository) {
    suspend operator fun invoke(ids: List<String>): AppResult<TrashRestoreOutcome> = trash.restoreZip(ids)
}
