package com.pion.phonecleaner.feature.antivirus.scan

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.security.ScanFailure
import com.pion.phonecleaner.domain.model.security.SecurityScanPhase
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import com.pion.phonecleaner.domain.repository.SecurityScanRepository
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The scan screen (`docs/screens/15-antivirus.md` §1.2). Replaces a 559-line Activity, a ViewModel
 * whose only used field is a `StateFlow`, and three entry gates re-implemented in two callers.
 *
 * **No `init` block starts the scan**: it can start only once all three gates are clear, and two of
 * them are platform truth the composable has to report first. `ScreenStarted` and `GateStateReported`
 * converge on one [evaluateGates].
 *
 * ANALYTICS — deliberately absent. The funnel this screen would fill (consent answered, no network,
 * scan stopped) has **no arm** in `AnalyticsEvent`, a sealed interface in a file this cluster does not
 * own, so a local event type is not an option either; the arms are reported for the owner to add
 * rather than an unused `AnalyticsRepository` injected to look as though it were wired.
 */
class AntivirusScanViewModel(
    private val repository: SecurityScanRepository,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger,
) : MviViewModel<AntivirusScanState, AntivirusScanIntent, AntivirusScanEffect>(
    AntivirusScanState(),
    log,
) {

    private var gateJob: Job? = null
    private var scanJob: Job? = null

    /** Last platform report. Inputs, not UI state — nothing renders them (MVI §4). */
    private var isOnline = false
    private var hasStorageAccess = false

    /** Set by `GrantStoragePressed`, so a *declined* grant is not an endless gate. See [evaluateGates]. */
    private var hasAskedForStorage = false

    override fun onIntent(intent: AntivirusScanIntent) {
        when (intent) {
            AntivirusScanIntent.ScreenStarted ->
                launchSafely { markFeatureUsed(FeatureId.Antivirus) }

            is AntivirusScanIntent.GateStateReported -> {
                isOnline = intent.isOnline
                hasStorageAccess = intent.hasStorageAccess
                evaluateGates()
            }

            AntivirusScanIntent.ConsentAccepted -> answerConsent(granted = true)
            AntivirusScanIntent.ConsentRejected -> answerConsent(granted = false)
            is AntivirusScanIntent.ConsentLinkTapped ->
                sendEffect(AntivirusScanEffect.OpenUrl(intent.url))

            AntivirusScanIntent.GrantStoragePressed -> {
                hasAskedForStorage = true
                sendEffect(AntivirusScanEffect.RequestStorageAccess)
            }

            AntivirusScanIntent.RetryPressed -> {
                setState { copy(phase = SecurityScanPhase.Idle) }
                evaluateGates()
            }

            AntivirusScanIntent.BackPressed ->
                if (currentState.canStop) {
                    setState { copy(isStopConfirmVisible = true) }
                } else {
                    sendEffect(AntivirusScanEffect.NavigateBack)
                }

            AntivirusScanIntent.StopConfirmed -> stop()
            AntivirusScanIntent.StopDismissed -> setState { copy(isStopConfirmVisible = false) }
        }
    }

    /**
     * Idempotent — it arrives on every `ON_START`. A running scan is left alone, and a **failed** one
     * is restarted by `RetryPressed` only: re-running it on return turns a persistent failure into a
     * loop. The gate order is [scanGateDecision]'s.
     */
    private fun evaluateGates() {
        if (currentState.isRunning || currentState.phase is SecurityScanPhase.Failed) return
        gateJob?.cancel()
        gateJob = launchSafely(onError = ::report) {
            val decision = scanGateDecision(
                isOnline,
                repository.consent(),
                hasStorageAccess,
                hasAskedForStorage,
            )
            when (decision) {
                is ScanGateDecision.Blocked -> block(decision.gate)
                ScanGateDecision.AskConsent ->
                    setState { copy(blockingGate = ScanGate.Consent, isConsentDialogVisible = true) }

                is ScanGateDecision.Run -> {
                    setState {
                        copy(
                            blockingGate = null,
                            isConsentDialogVisible = false,
                            coverage = decision.coverage,
                        )
                    }
                    startScan()
                }
            }
        }
    }

    private fun block(gate: ScanGate) =
        setState { copy(blockingGate = gate, isConsentDialogVisible = false) }

    /** A rejection is recorded, so it is remembered rather than re-asked on every entry (§0.5). */
    private fun answerConsent(granted: Boolean) {
        setState { copy(isConsentDialogVisible = false) }
        launchSafely(onError = ::report) {
            when (val recorded = repository.recordConsent(granted)) {
                is AppResult.Failure -> report(recorded.error)
                is AppResult.Success -> evaluateGates()
            }
        }
    }

    /**
     * One cancellable job, with the whole flow inside it (§1.2). `withTimeoutOrNull` **wraps** the
     * collection rather than racing it: the competitor's parallel timer is cancelled only on success,
     * so it fires a stray error five minutes after every failure. The elapsed ticker is a structural
     * **child** of this job, never a field.
     */
    private fun startScan() {
        if (currentState.isRunning) return
        scanJob?.cancel()
        setState { copy(phase = SecurityScanPhase.Preparing, elapsedMs = 0L) }
        scanJob = launchSafely(onError = { fail(ScanFailure.Unknown) }) {
            val ticker = launch {
                var elapsed = 0L
                while (isActive) {
                    delay(ELAPSED_TICK_MS)
                    elapsed += ELAPSED_TICK_MS
                    setState { copy(elapsedMs = elapsed) }
                }
            }
            try {
                var last: SecurityScanPhase = SecurityScanPhase.Idle
                val completed = withTimeoutOrNull(SCAN_BUDGET_MS) {
                    repository.scan().collect { phase ->
                        last = phase
                        setState { copy(phase = phase) }
                    }
                    true
                } == true
                when {
                    !completed -> fail(ScanFailure.Timeout)
                    last is SecurityScanPhase.Finished ->
                        finish((last as SecurityScanPhase.Finished).findings)

                    last is SecurityScanPhase.Failed -> Unit // already rendered
                    else -> fail(ScanFailure.Unknown) // the flow ended with no terminal phase
                }
            } finally {
                // The ticker is a structural CHILD, so it is cancelled with the job — but a job
                // whose body has returned still waits for its children, so without this the
                // elapsed counter runs on after a finished or failed scan and `scanJob` never
                // completes. `docs/screens/15-antivirus.md` §1.2 writes this `finally` for the
                // same reason; it was missing here.
                ticker.cancel()
            }
        }
    }

    /**
     * Persists **before** navigating (§0.4). The result screen has no list-bearing argument — this
     * write is what it will read — so a failed write is not a navigable outcome.
     */
    private suspend fun finish(findings: ImmutableList<ThreatVerdict>) {
        when (val recorded = repository.recordScanFinished(findings)) {
            is AppResult.Failure -> {
                report(recorded.error)
                fail(ScanFailure.Unknown)
            }

            is AppResult.Success -> sendEffect(
                if (findings.isEmpty()) {
                    AntivirusScanEffect.NavigateToCleanResult(NOTHING_FOUND)
                } else {
                    AntivirusScanEffect.NavigateToResult(findings.size)
                },
            )
        }
    }

    /** Cancelling the job cancels the flow, which runs the client's `awaitClose`. */
    private fun stop() {
        scanJob?.cancel()
        scanJob = null
        setState { copy(isStopConfirmVisible = false, phase = SecurityScanPhase.Idle, elapsedMs = 0L) }
        sendEffect(AntivirusScanEffect.NavigateBack)
    }

    private fun fail(reason: ScanFailure) = setState { copy(phase = SecurityScanPhase.Failed(reason)) }

    private fun report(error: AppError) = sendEffect(AntivirusScanEffect.ShowMessage(error))

    private companion object {
        /** The competitor's own budget, ported: `TimeUnit.MINUTES.toMillis(5)` (§3.3). */
        const val SCAN_BUDGET_MS = 5 * 60 * 1000L

        /** One second. Chosen here — no source names a tick — and one recomposition per second. */
        const val ELAPSED_TICK_MS = 1_000L

        /** Zero findings is a *clean result*, not an error: nothing found, nothing freed. */
        val NOTHING_FOUND = CleanupSummary(
            feature = FeatureId.Antivirus,
            freedBytes = 0L,
            itemCount = 0,
            outcome = CleanupOutcome.NothingFound,
        )
    }
}
