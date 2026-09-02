package com.pion.phonecleaner.feature.antivirus.scan

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.security.ScanCoverage
import com.pion.phonecleaner.domain.model.security.ScanFailure
import com.pion.phonecleaner.domain.model.security.SecurityScanPhase

/**
 * Which precondition is blocking the scan (`docs/screens/15-antivirus.md` §1.1).
 *
 * The three gates and their **order** — network, then consent, then storage — are the competitor's,
 * kept so the funnel still maps one-to-one onto the research chapter
 * (`docs/reverse-engineering/15-antivirus.md` §3.0). What changes is where they live: the competitor
 * re-implements all three in *two* different callers, so a deep link reaches neither.
 */
enum class ScanGate { Network, Consent, StoragePermission }

/**
 * The scan screen's state. Replaces four Activity fields and a four-state sealed class that drives
 * behaviour instead of describing it (§1.1's folding table).
 *
 * `hasStop`, `cloudScanClient`, the synthetic-progress `Job` and the five-minute timeout `Job` are
 * **all gone** from state: the first has no path left to guard, the second is a `single`, and the two
 * jobs are structural children of the scan job.
 */
@Immutable
data class AntivirusScanState(
    val phase: SecurityScanPhase = SecurityScanPhase.Idle,
    /** `null` means nothing blocks and the scan may run. */
    val blockingGate: ScanGate? = null,
    val isConsentDialogVisible: Boolean = false,
    val isStopConfirmVisible: Boolean = false,
    /** What the scan can reach with the grants held right now. Surfaced, never silent (§8.4). */
    val coverage: ScanCoverage = ScanCoverage.Unknown,
    /** Milliseconds since this scan started. Rendered, so the ticker that writes it is not dead. */
    val elapsedMs: Long = 0L,
) : UiState {

    val isRunning: Boolean
        get() = phase is SecurityScanPhase.Preparing || phase is SecurityScanPhase.Scanning

    /**
     * `0f..1f`, or `null` for indeterminate.
     *
     * **The first half is not synthesised.** The competitor animates 0 → 50 % over 300 000 ms and
     * then maps the real counts onto 50 → 100 %, so the bar is a fiction for up to five minutes and
     * then jumps to half full (§1.5).
     */
    val progress: Float?
        get() = (phase as? SecurityScanPhase.Scanning)
            ?.let { if (it.total <= 0) null else (it.current.toFloat() / it.total).coerceIn(0f, 1f) }

    /** The app currently in flight. Empty while preparing — the screen falls back to its own copy. */
    val scanningLabel: String get() = (phase as? SecurityScanPhase.Scanning)?.label.orEmpty()

    val failure: ScanFailure? get() = (phase as? SecurityScanPhase.Failed)?.reason

    val canStop: Boolean get() = isRunning
}

sealed interface AntivirusScanIntent : UiIntent {
    data object ScreenStarted : AntivirusScanIntent

    /**
     * Platform truth, reported by the composable on every `ON_START` (MVI §4).
     *
     * `ON_START` rather than once: the storage grant is given on a *system settings screen* the user
     * returns from, and the competitor's equivalent check runs once and never again.
     */
    data class GateStateReported(
        val hasStorageAccess: Boolean,
        val isOnline: Boolean,
    ) : AntivirusScanIntent

    data object ConsentAccepted : AntivirusScanIntent
    data object ConsentRejected : AntivirusScanIntent
    data class ConsentLinkTapped(val url: String) : AntivirusScanIntent
    data object GrantStoragePressed : AntivirusScanIntent

    /** Actually retries. The competitor's button of this name calls `finish()` (§3.1). */
    data object RetryPressed : AntivirusScanIntent
    data object BackPressed : AntivirusScanIntent
    data object StopConfirmed : AntivirusScanIntent
    data object StopDismissed : AntivirusScanIntent
}

sealed interface AntivirusScanEffect : UiEffect {
    data class NavigateToResult(val findingCount: Int) : AntivirusScanEffect

    /** The zero-finding path: the shared clean-result route, with nothing freed and nothing found. */
    data class NavigateToCleanResult(val summary: CleanupSummary) : AntivirusScanEffect

    data object NavigateBack : AntivirusScanEffect
    data object RequestStorageAccess : AntivirusScanEffect
    data class OpenUrl(val url: String) : AntivirusScanEffect

    /**
     * Carries the error. Reading `state` in the collector reads the pre-failure value: the collector
     * runs one main-queue turn after `sendEffect` and a frame before the matching `setState` renders
     * (MVI §4, `LLM.md` §5).
     *
     * UNKNOWN — `docs/screens/15-antivirus.md` types this `UiText`. **No `UiText` exists** in
     * `:core:mvi`, `:core:ui` or `:core:common` (grepped), and neither module is this cluster's to
     * add one to. `AppError` is what every shipped Effect of this shape carries, and `:core:ui`'s
     * `ErrorMessages` already maps it to copy.
     */
    data class ShowMessage(val error: AppError) : AntivirusScanEffect
}
