package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.map
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.trash.asDeleteOutcome
import com.pion.phonecleaner.domain.repository.CleanupLedger
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.TrashRepository

/**
 * Delete what the user selected — into the bin when one exists, permanently when it does not (plan
 * `260908-0801-trash-bin`, Phase 07) — and **record what that actually freed**
 * (`docs/screens/14-file-tools-and-app-manager.md` §8, rule 1).
 *
 * **The branch is HERE, not inside a decorator on [FileDeleter]** (engineer decision E3):
 * `RemoveFindingUseCase` must bypass the bin entirely (owner decision D5) and the trash screen's own
 * "delete forever" must still reach the real deleter, so an exception that has to be visible has to
 * be visible at a call site, not hidden inside a Koin binding.
 *
 * On the trash path [CleanupLedger.record] is **never** called: nothing was freed, the bytes are
 * still on the volume, in the bin. They are credited exactly once, later, in
 * [DeleteTrashForeverUseCase] / [PurgeExpiredTrashUseCase], when they actually leave the device.
 *
 * A per-file failed move in [com.pion.phonecleaner.domain.model.trash.TrashMoveOutcome.failedPaths]
 * is never retried against [deleter]: that file is untouched, still where the user left it, and
 * [DeleteOutcome.Deleted.failedPaths] carries the failure to the screen instead of silently deleting
 * it forever. The ledger is still fed **at the source, as a raw `Long`**. The competitor accumulates
 * its lifetime counter by re-parsing the formatted display string the previous screen produced
 * (`md.g4.e()`), losing about 5 % to `DecimalFormat("###.0")` and defaulting an unrecognised unit to
 * MB — **`parseBytes` does not exist anywhere in this app** (`docs/system-architecture.md` §4.2).
 *
 * `PendingConsent` records nothing, because nothing was deleted yet: the screen launches the consent
 * `IntentSender` and calls this again, and the second pass is the one that counts the bytes. That
 * two-call protocol is [FileDeleter]'s, and it is idempotent on purpose. It is also unreachable on the
 * trash path: [FileDeleter] is never called there, so no delete request is ever built.
 */
class DeleteFilesUseCase(
    private val deleter: FileDeleter,
    private val trash: TrashRepository,
    private val ledger: CleanupLedger,
) {

    /** Executes the confirmed mode. A refused trash move never authorizes permanent deletion. */
    suspend operator fun invoke(files: List<ScannedFile>, source: FeatureId, requireTrash: Boolean): AppResult<DeleteOutcome> {
        if (requireTrash) {
            return trash.trashFiles(files, source).map { it.asDeleteOutcome() }
        }
        val result = deleter.delete(files)
        val outcome = (result as? AppResult.Success)?.value
        if (outcome is DeleteOutcome.Deleted && outcome.freedBytes > 0L) {
            ledger.record(outcome.freedBytes)
        }
        return result
    }
}
