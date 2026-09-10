package com.pion.phonecleaner.domain.model.junk

import kotlinx.collections.immutable.ImmutableList

/**
 * What a clean actually did — both numbers, always.
 *
 * The competitor reports `if (cleanedSize > 0) cleanedSize else totalSize`
 * (`MenaremovActivity.java:325-328`), so a total failure is reported to the user **and to the
 * lifetime ledger** as a total success (`docs/screens/12-junk-cleaning.md` §5.5 Delta C3). It also
 * keeps no record of what failed. [failedPaths] is that record, and it reaches the result screen.
 */
data class CleanOutcome(
    val freedBytes: Long,
    val deletedCount: Int,
    val failedPaths: ImmutableList<String>,
    /**
     * True when the paths went into the bin instead of leaving the device — set from
     * `TrashRepository.isAvailable()` at the moment `CleanJunkUseCase` ran, which is the fact, not
     * from a permission read, which is only advisory. [freedBytes] is then "bytes moved": nothing may
     * credit them to `CleanupLedger` or call them freed, and the result screen must say
     * `CleanupOutcome.MovedToTrash` rather than `Cleaned`. Defaulted so the no-trash branch's own
     * construction sites compile unchanged.
     */
    val recoverable: Boolean = false,
)
