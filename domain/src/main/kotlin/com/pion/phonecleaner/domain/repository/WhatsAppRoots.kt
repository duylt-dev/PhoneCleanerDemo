package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.file.WhatsAppBucketId
import kotlinx.collections.immutable.ImmutableList

/**
 * Where each bucket's files live, as **relative suffixes** joined to a root [StorageRootProvider]
 * resolved (`docs/screens/14-file-tools-and-app-manager.md` §6.2).
 *
 * Two rules it exists to enforce:
 *
 *  * **Never a literal `/storage/emulated/0`.** The competitor hard-codes that prefix, so a secondary
 *    volume or a non-zero user profile is a different absolute path and its cleaner reads nothing.
 *  * **The catalogue is read through this port, never pasted into this repository.** The path table
 *    is the competitor's, and it is treated exactly as the junk rule catalogue was: an interface here,
 *    and an implementation in `:data` that carries no copied table until the owner decides
 *    (PENDING OWNER DECISION 1 — whether a competitor-derived catalogue is reused at all).
 *
 * DECLARED IN `filesDataModule`.
 */
interface WhatsAppRoots {

    /**
     * Relative path suffixes for one bucket — `"Media/WhatsApp Video"` and the like, deduplicated.
     * Empty means the catalogue names nothing for that bucket, which the scanner reports as an empty
     * bucket rather than as a failure.
     */
    suspend fun suffixesFor(bucket: WhatsAppBucketId): ImmutableList<String>
}
