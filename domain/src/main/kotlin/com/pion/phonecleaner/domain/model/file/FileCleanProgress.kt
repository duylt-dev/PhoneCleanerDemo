package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList

/**
 * What a batched delete reports while it runs, one emission per batch.
 *
 * Per-batch emission is what makes a live "remaining size" readout honest: the number moves because
 * bytes were actually freed. The competitor posts its remaining size per file and its observer
 * **discards the value it was posted**, so the readout never moves at all
 * (`docs/screens/14-file-tools-and-app-manager.md` §6.1).
 *
 * [NeedsConsent] is not an error. On API 30+ a batch of MediaStore rows can only be removed through
 * `MediaStore.createDeleteRequest`, whose `IntentSender` only an Activity can launch, so the flow
 * stops at that batch and the screen re-issues the clean after the round trip — the two-call protocol
 * `FileDeleter` documents. In the all-files branch the arm simply never fires
 * (`docs/system-architecture.md` §8.4).
 */
sealed interface FileCleanProgress {

    data class Deleted(val ids: ImmutableList<String>, val freedBytes: Long) : FileCleanProgress

    /** Paths a strategy could not remove. Reported, never swallowed. */
    data class Failed(val paths: ImmutableList<String>) : FileCleanProgress

    data class NeedsConsent(
        val request: PendingIntentToken,
        val ids: ImmutableList<String>,
    ) : FileCleanProgress

    data class Finished(
        val freedBytes: Long,
        val deletedCount: Int,
        val failedCount: Int,
        /**
         * True when [freedBytes] went into the bin instead of leaving the device (plan
         * `260908-0801-trash-bin`, Phase 07) — "bytes moved", never credited to `CleanupLedger`.
         * Defaulted so existing construction sites compile unchanged.
         */
        val recoverable: Boolean = false,
    ) : FileCleanProgress
}
