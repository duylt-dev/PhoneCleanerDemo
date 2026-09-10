package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.domain.model.file.ZipFileOutcome
import com.pion.phonecleaner.domain.model.file.ZipFileRequest
import com.pion.phonecleaner.domain.repository.FileZipper

class CreateZipFileUseCase(
    private val zipper: FileZipper,
) {
    suspend operator fun invoke(request: ZipFileRequest): AppResult<ZipFileOutcome> {
        if (request.files.isEmpty()) return AppError.NotFound("zip-input").asFailure()
        if (request.files.size > MAX_FILES) return AppError.Unexpected("too-many-files").asFailure()
        return zipper.createZip(request)
    }

    companion object {
        const val MAX_FILES = 10
    }
}
