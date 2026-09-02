package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.ScannedFile

/**
 * Content digest of one file, for duplicate detection. Replaces `od.s0`; declared once in
 * `storageDataModule` as `Md5FileDigest` (`docs/system-architecture.md` §5.6).
 *
 * `DuplicateFinder` (`filesDataModule`) composes this with [StorageScanner] and
 * [MediaStoreRepository]; the digest runs only over the surfaces those two actually yield
 * (`docs/screens/14-file-tools-and-app-manager.md:80`).
 */
interface FileDigest {
    /** Hex digest, or a failure carrying the storage error. Never throws across the boundary. */
    suspend fun digest(file: ScannedFile): AppResult<String>
}
