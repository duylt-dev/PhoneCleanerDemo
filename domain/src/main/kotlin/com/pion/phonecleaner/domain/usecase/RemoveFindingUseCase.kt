package com.pion.phonecleaner.domain.usecase

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.DeleteOutcome
import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.security.RemovalOutcome
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import com.pion.phonecleaner.domain.repository.FileDeleter
import com.pion.phonecleaner.domain.repository.SecurityScanRepository

/**
 * Removes one finding — or says what has to happen for it to be removed
 * (`docs/screens/15-antivirus.md` §2.2, §0.6).
 *
 * **For an installed app this only validates.** The actual removal is the system uninstall dialog,
 * which no code here can perform; the screen raises the Effect, the row stays greyed, and a
 * `PACKAGE_REMOVED` broadcast resolves it. For a loose `.apk` the deletion goes through the one
 * [FileDeleter] in the app — the security cluster does not own a second deleter
 * (`docs/system-architecture.md` §4.5).
 *
 * Registered `factoryOf(::RemoveFindingUseCase)` in `domainModule`; that file belongs to another
 * owner, so the line is reported rather than added here (`LLM.md` §6.4).
 */
class RemoveFindingUseCase(
    private val repository: SecurityScanRepository,
    private val fileDeleter: FileDeleter,
) {

    suspend operator fun invoke(finding: ThreatVerdict): AppResult<RemovalOutcome> {
        if (finding.isInstalledApp) {
            return AppResult.Success(RemovalOutcome.UninstallRequired(finding.packageName))
        }
        if (finding.apkPath.isBlank()) {
            // A finding with neither a package name nor a path cannot be acted on. Reported, not
            // swallowed: the competitor's equivalent branch removes the wrong row instead.
            return AppResult.Failure(AppError.NotFound(finding.md5))
        }
        return when (val deletion = fileDeleter.delete(listOf(finding.asScannedFile()))) {
            is AppResult.Failure -> deletion
            is AppResult.Success -> resolve(finding, deletion.value)
        }
    }

    private suspend fun resolve(
        finding: ThreatVerdict,
        outcome: DeleteOutcome,
    ): AppResult<RemovalOutcome> = when (outcome) {
        is DeleteOutcome.Deleted ->
            if (outcome.ids.isEmpty()) {
                // `Deleted` with nothing in `ids` means every path failed. The competitor's deleter
                // returns 0L through a `catch { printStackTrace() }` and the row disappears anyway.
                AppResult.Failure(AppError.Storage(path = finding.apkPath))
            } else {
                when (val forgotten = repository.forget(finding.md5)) {
                    is AppResult.Failure -> forgotten
                    is AppResult.Success -> AppResult.Success(RemovalOutcome.Removed(outcome.freedBytes))
                }
            }

        DeleteOutcome.NothingResolved -> AppResult.Failure(AppError.NotFound(finding.apkPath))

        // Unreachable for `FileOrigin.PlainFile` — `createDeleteRequest` is a MediaStore path — but
        // the arm is handled rather than defaulted, because omitting one is what makes the storage
        // branch expensive to switch (`docs/system-architecture.md` §8.4). There is no Effect on
        // this screen that could launch the `IntentSender`, so it is reported as a denial.
        is DeleteOutcome.PendingConsent -> AppResult.Failure(AppError.PermissionDenied())
    }

    /**
     * The one projection between the two models. [ThreatVerdict.md5] is the id on both sides, so a
     * `DeleteOutcome.Deleted.ids` entry can be matched back without a second lookup.
     */
    private fun ThreatVerdict.asScannedFile(): ScannedFile = ScannedFile(
        id = md5,
        path = apkPath,
        name = label,
        sizeBytes = apkSizeBytes,
        kind = FileKind.Apk,
        origin = FileOrigin.PlainFile,
    )
}
