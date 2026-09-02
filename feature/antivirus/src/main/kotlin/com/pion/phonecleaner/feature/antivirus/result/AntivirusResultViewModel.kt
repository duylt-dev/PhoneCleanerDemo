package com.pion.phonecleaner.feature.antivirus.result

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.security.RemovalOutcome
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import com.pion.phonecleaner.domain.repository.SecurityScanRepository
import com.pion.phonecleaner.domain.usecase.IgnoreFindingUseCase
import com.pion.phonecleaner.domain.usecase.RemoveFindingUseCase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Job

/**
 * The result screen (`docs/screens/15-antivirus.md` §2.2). Replaces a 403-line Activity that has no
 * ViewModel at all — its type parameter is the bare base class and all four pieces of its state are
 * Activity fields.
 *
 * **No route argument carries the list.** `observeLastResult()` is the only read path, so process
 * death, rotation and a cold deep link render the same thing; the `findingCount` argument seeds the
 * headline for one frame and is overwritten by the first emission.
 *
 * ANALYTICS — deliberately absent, exactly as on the scan screen. The events this screen would fill
 * (a finding removed, a finding ignored, a rescan) have **no arm** in `AnalyticsEvent`, a sealed
 * interface in a file this cluster does not own, so a local event type is not an option either. The
 * arms are reported for the owner to add rather than an unused `AnalyticsRepository` injected to
 * look as though it were wired.
 */
class AntivirusResultViewModel(
    savedState: SavedStateHandle,
    private val repository: SecurityScanRepository,
    private val removeFinding: RemoveFindingUseCase,
    private val ignoreFinding: IgnoreFindingUseCase,
    log: AppLogger,
) : MviViewModel<AntivirusResultState, AntivirusResultIntent, AntivirusResultEffect>(
    AntivirusResultState(expectedCount = savedState.findingCount()),
    log,
) {

    /** Not an `init` block: `ScreenStarted` starts the read, so the reducer is the only entry. */
    private var recordJob: Job? = null

    override fun onIntent(intent: AntivirusResultIntent) {
        when (intent) {
            AntivirusResultIntent.ScreenStarted -> observeRecord()

            is AntivirusResultIntent.FindingTapped ->
                // The in-flight flag is also the re-entry guard: a second tap on a row already
                // being removed opens nothing rather than queueing a second removal.
                if (intent.md5 !in currentState.removingMd5s) {
                    setState { copy(pendingRemovalMd5 = intent.md5) }
                }

            AntivirusResultIntent.RemovalDismissed -> setState { copy(pendingRemovalMd5 = null) }
            AntivirusResultIntent.RemovalConfirmed -> confirmRemoval()

            is AntivirusResultIntent.IgnorePressed -> ignore(intent.md5)
            is AntivirusResultIntent.PackageRemoved -> forgetRemovedPackage(intent.packageName)
            is AntivirusResultIntent.UninstallReturned -> lower(intent.md5)

            AntivirusResultIntent.RescanPressed ->
                sendEffect(AntivirusResultEffect.NavigateToScan)

            AntivirusResultIntent.BackPressed ->
                sendEffect(AntivirusResultEffect.NavigateBack)
        }
    }

    /** Idempotent — `ScreenStarted` arrives on every `ON_START`, and one collector is enough. */
    private fun observeRecord() {
        if (recordJob?.isActive == true) return
        recordJob = repository.observeLastResult().collectSafely(
            onError = { setState { copy(isLoading = false, error = it) } },
        ) { record ->
            setState {
                copy(
                    isLoading = false,
                    error = null,
                    findings = record?.findings ?: persistentListOf(),
                    scannedAtEpochMs = record?.finishedAtEpochMs,
                    // A row that has left the list cannot still be "removing": otherwise the flag
                    // outlives its row, which is how the competitor's pending field survives the
                    // empty branch and shows a second success toast on a stray broadcast (§2.5).
                    removingMd5s = removingMd5s
                        .filter { md5 -> record?.findings.orEmpty().any { it.md5 == md5 } }
                        .toImmutableSet(),
                )
            }
        }
    }

    /**
     * Raises the in-flight flag and clears the dialog **before** the work starts, and lowers the
     * flag in every failing arm — `onError` included. MVI §1: `onError` must lower every flag the
     * call raised, or the row is greyed for ever.
     *
     * For an installed app the use case only *validates*: the removal is the system dialog, so the
     * row stays greyed until a `PackageRemoved` or an `UninstallReturned` resolves it.
     */
    private fun confirmRemoval() {
        val finding = currentState.pendingRemoval ?: return
        setState {
            copy(
                pendingRemovalMd5 = null,
                removingMd5s = (removingMd5s + finding.md5).toImmutableSet(),
            )
        }
        launchSafely(onError = { failRemoval(finding, it) }) {
            when (val outcome = removeFinding(finding)) {
                is AppResult.Failure -> failRemoval(finding, outcome.error)
                is AppResult.Success -> apply(finding, outcome.value)
            }
        }
    }

    private fun apply(finding: ThreatVerdict, outcome: RemovalOutcome) = when (outcome) {
        is RemovalOutcome.UninstallRequired ->
            sendEffect(AntivirusResultEffect.LaunchUninstall(finding.md5, outcome.packageName))

        // The use case has already told the repository to forget the row, so the list emission
        // takes it away. The flag is lowered too, in case that emission is not the next one.
        is RemovalOutcome.Removed -> lower(finding.md5)
    }

    private fun failRemoval(finding: ThreatVerdict, error: AppError) {
        lower(finding.md5)
        sendEffect(AntivirusResultEffect.ShowMessage(error))
    }

    /** Additive, not a port: the competitor offers exactly one action per row (§2.5). */
    private fun ignore(md5: String) = launchSafely(onError = ::report) {
        when (val ignored = ignoreFinding(md5)) {
            is AppResult.Failure -> report(ignored.error)
            // The repository filters the row out of `observeLastResult`, so the list emission is
            // what removes it. Nothing is copied into state here.
            is AppResult.Success -> Unit
        }
    }

    /**
     * **Matched against the list.** An unmatched broadcast is ignored — this is the fix for the
     * competitor removing whatever its last-tapped field happens to hold, so that uninstalling any
     * app anywhere deletes the wrong row.
     */
    private fun forgetRemovedPackage(packageName: String) {
        if (packageName.isBlank()) return
        val matched = currentState.findings
            .filter { it.isInstalledApp && it.packageName == packageName }
            .map { it.md5 }
        if (matched.isEmpty()) return
        launchSafely(onError = ::report) {
            matched.forEach { md5 ->
                when (val forgotten = repository.forget(md5)) {
                    is AppResult.Failure -> report(forgotten.error)
                    is AppResult.Success -> Unit
                }
                lower(md5)
            }
        }
    }

    private fun lower(md5: String) = setState {
        copy(removingMd5s = removingMd5s.filterNot { it == md5 }.toImmutableSet())
    }

    private fun report(error: AppError) = sendEffect(AntivirusResultEffect.ShowMessage(error))
}
