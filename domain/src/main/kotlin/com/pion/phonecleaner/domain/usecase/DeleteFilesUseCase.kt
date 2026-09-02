package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.FileDeleter

/**
 * Delete what the user selected, and **record what that freed**
 * (`docs/screens/14-file-tools-and-app-manager.md` §8, rule 1).
 *
 * The ledger is fed **at the source, as a raw `Long`**. The competitor accumulates its lifetime
 * counter by re-parsing the formatted display string the previous screen produced (`md.g4.e()`),
 * losing about 5 % to `DecimalFormat("###.0")` and defaulting an unrecognised unit to MB —
 * **`parseBytes` does not exist anywhere in this app** (`docs/system-architecture.md` §4.2).
 *
 * `PendingConsent` records nothing, because nothing was deleted yet: the screen launches the consent
 * `IntentSender` and calls this again, and the second pass is the one that counts the bytes. That
 * two-call protocol is `FileDeleter`'s, and it is idempotent on purpose.
 */
class DeleteFilesUseCase(
    private val deleter: FileDeleter,
    private val ledger: CleanupLedger,
) {

    suspend operator fun invoke(files: List<ScannedFile>): AppResult<DeleteOutcome> {
        val result = deleter.delete(files)
        val outcome = (result as? AppResult.Success)?.value
        if (outcome is DeleteOutcome.Deleted && outcome.freedBytes > 0L) {
            ledger.record(outcome.freedBytes)
        }
        return result
    }
}
