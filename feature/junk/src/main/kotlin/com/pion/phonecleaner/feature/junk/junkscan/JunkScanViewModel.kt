package com.pion.phonecleaner.feature.junk.junkscan

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.junk.JunkScanMode
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.domain.repository.JunkRepository
import com.pion.phonecleaner.domain.repository.JunkSessionStore
import kotlinx.coroutines.Job

/**
 * The scan screen (`docs/screens/12-junk-cleaning.md` §3.2).
 *
 * `init` **observes and does not act** (MVI §3 rule 3): the scan needs a storage answer, so it starts
 * from `PermissionsResolved(true)`, never from `init`. That is the competitor's `d0()` fork
 * expressed as an intent instead of a 100 ms `delay` followed by a check.
 *
 * ANALYTICS — deliberately absent. §3.2 closes two funnel gaps with `ScanFinished(totalBytes,
 * durationMs, itemCount)` and `ScanCancelled(atPass)`, but `AnalyticsEvent`
 * (`:domain/repository/AnalyticsRepository.kt`) is a sealed interface with three arms and neither of
 * those is one of them. A sealed interface admits implementations only in its own module and
 * package, and that file belongs to another owner, so a local event type is not an option either —
 * the digest says so in as many words. The two arms are reported for the owner to add; nothing is
 * fabricated here, and no unused `AnalyticsRepository` is injected to look as though it were wired.
 */
class JunkScanViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: JunkRepository,
    private val session: JunkSessionStore,
    log: AppLogger,
) : MviViewModel<JunkScanState, JunkScanIntent, JunkScanEffect>(
    JunkScanState(mode = savedStateHandle.scanMode()),
    log,
) {

    private var scanJob: Job? = null

    /**
     * `leaveIfReady()` is reachable from both `Finished` and `CompletionAnimationFinished`, and
     * whichever arrives second finds `canLeave` already true. §8.2 requires **exactly one**
     * `NavigateToReview`, so the latch is not optional.
     */
    private var hasNavigated = false

    override fun onIntent(intent: JunkScanIntent) {
        when (intent) {
            is JunkScanIntent.PermissionsResolved -> onPermissionsResolved(intent.granted)
            JunkScanIntent.GrantStorageTapped -> sendEffect(JunkScanEffect.RequestStoragePermission)
            JunkScanIntent.CompletionAnimationFinished -> {
                setState { copy(completionAnimationFinished = true) }
                leaveIfReady()
            }

            JunkScanIntent.BackPressed -> onBackPressed()
            JunkScanIntent.StopConfirmed -> onStopConfirmed()
            JunkScanIntent.StopDismissed -> setState { copy(isStopConfirmVisible = false) }
            JunkScanIntent.RetryTapped -> startScan()
        }
    }

    /**
     * Denial is a **state**, not an exit. The competitor calls `finish()`
     * (`TaribrActivity.java:151`), so the user taps "Junk Cleaner" and the screen vanishes
     * (Delta S5).
     *
     * Idempotent: this arrives on every `ON_START`, and a scan already running or already finished
     * is left alone. A `Failed` scan is restarted by `RetryTapped`, not by returning to the screen —
     * otherwise a persistent failure becomes a loop.
     */
    private fun onPermissionsResolved(granted: Boolean) {
        if (!granted) {
            scanJob?.cancel()
            setState { copy(phase = JunkScanPhase.PermissionRequired) }
            return
        }
        val phase = currentState.phase
        if (phase != JunkScanPhase.CheckingPermission && phase != JunkScanPhase.PermissionRequired) return
        startScan()
    }

    /** Cancel and replace (MVI §3): a second start means "that one, now", not "both of them". */
    private fun startScan() {
        scanJob?.cancel()
        hasNavigated = false
        setState {
            copy(
                phase = JunkScanPhase.Scanning,
                currentCategory = null,
                currentPath = "",
                scannedCount = 0,
                candidateCount = CANDIDATE_COUNT_UNKNOWN,
                foundBytes = 0L,
                passesFinished = 0,
                completionAnimationFinished = false,
                error = null,
            )
        }
        scanJob = repository.scan().collectSafely(
            // The busy flag is lowered on this path too. A stranded `isScanning` would also be the
            // re-entry guard, so the screen would refuse the very action that clears it (MVI §1).
            onError = { error -> setState { copy(phase = JunkScanPhase.Failed, error = error) } },
        ) { progress -> reduce(progress) }
    }

    private fun reduce(progress: ScanProgress) {
        when (progress) {
            is ScanProgress.PassStarted -> setState {
                copy(
                    currentCategory = progress.category,
                    scannedCount = 0,
                    candidateCount = CANDIDATE_COUNT_UNKNOWN,
                )
            }

            is ScanProgress.Candidate -> setState {
                copy(
                    currentPath = progress.path,
                    scannedCount = progress.index,
                    candidateCount = progress.total,
                    foundBytes = foundBytes + progress.sizeBytes,
                )
            }

            is ScanProgress.PassFinished -> setState { copy(passesFinished = passesFinished + 1) }

            is ScanProgress.Finished -> {
                // The session store is written BEFORE the phase flips, so a `NavigateToReview`
                // raised by the very next line can never reach a screen with nothing to read.
                session.put(progress.categories, progress.totalBytes)
                setState {
                    copy(
                        phase = JunkScanPhase.Finished,
                        // Authoritative: conflation may have dropped candidate emissions, so the
                        // running sum is an approximation and this is the measurement.
                        foundBytes = progress.totalBytes,
                    )
                }
                leaveIfReady()
            }
        }
    }

    /**
     * Back while scanning is a confirm, not a Toast. The competitor blocks Back with a Toast
     * (`TaribrActivity.java:447-454`), which traps the user inside a walk with no duration cap
     * (Delta S7).
     */
    private fun onBackPressed() {
        if (currentState.isScanning) {
            setState { copy(isStopConfirmVisible = true) }
        } else {
            sendEffect(JunkScanEffect.NavigateBack)
        }
    }

    private fun onStopConfirmed() {
        // `CancellationException` propagates through `collectSafely` untouched and the walk stops at
        // its next `ensureActive()` — cancellable by construction, not by a flag the walk polls.
        scanJob?.cancel()
        scanJob = null
        setState { copy(isStopConfirmVisible = false) }
        sendEffect(JunkScanEffect.NavigateBack)
    }

    private fun leaveIfReady() {
        if (hasNavigated || !currentState.canLeave) return
        hasNavigated = true
        sendEffect(JunkScanEffect.NavigateToReview)
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}

/**
 * Reads the route argument out of `SavedStateHandle` into the INITIAL state, never copied in a frame
 * later (MVI §3 rule 5).
 *
 * UNKNOWN — the argument key. `docs/screens/12-junk-cleaning.md` §3.2 writes
 * `savedStateHandle.get<JunkScanMode>(Routes.ARG_SCAN_MODE)`, and `Routes.kt` lives in `:app`, which
 * this module may not see (`LLM.md` §2) and which has no such constant yet. With type-safe
 * navigation the key is the route data class's own property name, so the reported route declaration
 * names its property `mode` and this constant matches it. Both shapes are read because
 * Navigation-Compose stores an enum argument as its `name`, while a directly-set handle holds the
 * enum itself.
 */
private fun SavedStateHandle.scanMode(): JunkScanMode = when (val raw = get<Any?>(ARG_MODE)) {
    is JunkScanMode -> raw
    is String -> JunkScanMode.entries.firstOrNull { it.name == raw } ?: JunkScanMode.Review
    else -> JunkScanMode.Review
}

private const val ARG_MODE = "mode"
