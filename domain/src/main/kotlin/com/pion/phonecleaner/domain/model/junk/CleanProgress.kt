package com.pion.phonecleaner.domain.model.junk

import com.pion.phonecleaner.core.common.error.AppError

/**
 * What a clean reports while it runs, one emission per path.
 *
 * Per-path emission is what makes cancelling mid-delete safe: whatever was freed is already folded
 * into the screen's `freedBytes` and into the ledger when the use case's `finally` runs
 * (`docs/screens/12-junk-cleaning.md` §5.2). The competitor loses `cleanedSize` entirely on
 * `onDestroy` and never resets its caches, because the method that would have done so never runs
 * (Delta C10).
 *
 * **[Failed] is a first-class outcome, not silence.** The competitor's entire error strategy in this
 * cluster is `catch (Exception) { printStackTrace(); }` at five sites and no error ever reaches the
 * user (`docs/reverse-engineering/12-junk-cleaning.md` §12 finding 28).
 */
sealed interface CleanProgress {

    data class Deleted(val path: String, val freedBytes: Long) : CleanProgress

    data class Failed(val path: String, val reason: AppError) : CleanProgress

    data class Finished(val outcome: CleanOutcome) : CleanProgress
}
