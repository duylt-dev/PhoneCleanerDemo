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

    /**
     * Hex digest of the first [maxBytes] bytes, or a failure carrying the storage error. Never
     * throws across the boundary.
     *
     * **[maxBytes] is what makes the two-phase pass of `docs/screens/14` §2.2 possible**: a head
     * digest over [HEAD_BYTES] separates same-size-different-content files for the price of one
     * buffer, and only the survivors are read end to end. Without it a duplicate scan over a whole
     * volume reads every same-size file in full — the competitor's shape, and the reason its hashing
     * loop needs a 4 s escape hatch that reports truncation as success.
     *
     * A file no larger than the limit is read whole, so its head digest **is** its full digest and a
     * second pass over it would be pure waste. Callers rely on that.
     */
    suspend fun digest(file: ScannedFile, maxBytes: Long = FULL_FILE): AppResult<String>

    companion object {
        /** Read to EOF. The default, and what every caller outside the duplicate pipeline wants. */
        const val FULL_FILE: Long = Long.MAX_VALUE

        /**
         * 64 KiB, the head-digest window `docs/screens/14-file-tools-and-app-manager.md` §2.2 names.
         * It lives here rather than in the finder because the "a smaller file needs no second pass"
         * rule above is a property of this port, not of one of its callers.
         */
        const val HEAD_BYTES: Long = 64L * 1024L
    }
}
