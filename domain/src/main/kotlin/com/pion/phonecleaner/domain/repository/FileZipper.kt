package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.ZipFileOutcome
import com.pion.phonecleaner.domain.model.file.ZipFileRequest

interface FileZipper {
    suspend fun createZip(request: ZipFileRequest): AppResult<ZipFileOutcome>
}
