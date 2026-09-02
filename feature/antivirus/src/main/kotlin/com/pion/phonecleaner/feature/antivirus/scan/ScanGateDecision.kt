package com.pion.phonecleaner.feature.antivirus.scan

import com.pion.phonecleaner.domain.model.security.ScanConsentState
import com.pion.phonecleaner.domain.model.security.ScanCoverage

/** What the three gates say, given everything currently known. */
internal sealed interface ScanGateDecision {
    data class Blocked(val gate: ScanGate) : ScanGateDecision
    data object AskConsent : ScanGateDecision
    data class Run(val coverage: ScanCoverage) : ScanGateDecision
}

/**
 * The three gates, in the competitor's order — network, then consent, then storage — so the funnel
 * still maps one-to-one onto `docs/reverse-engineering/15-antivirus.md` §3.0.
 *
 * A pure function of four booleans-worth of input, so the whole funnel is a unit test with no fakes.
 * It stays in this package rather than `:domain/policy/` (`LLM.md` §4) because it decides *this
 * screen's* preconditions and names [ScanGate], a contract type of this screen; a `:domain` policy
 * could not name it, and inverting that dependency would put a screen's flow in the domain layer.
 *
 * **The storage gate asks once and does not trap.** Once [hasAskedForStorage] is true the scan runs
 * with [ScanCoverage.InstalledAppsOnly] and the screen says what was skipped: `system-architecture.md`
 * §8.4 makes that mandatory in the default branch, and the alternative is the competitor's dead end —
 * a settings funnel with no way past it (§1.5).
 */
internal fun scanGateDecision(
    isOnline: Boolean,
    consent: ScanConsentState,
    hasStorageAccess: Boolean,
    hasAskedForStorage: Boolean,
): ScanGateDecision = when {
    !isOnline -> ScanGateDecision.Blocked(ScanGate.Network)
    consent == ScanConsentState.Unanswered -> ScanGateDecision.AskConsent
    consent == ScanConsentState.Rejected -> ScanGateDecision.Blocked(ScanGate.Consent)
    !hasStorageAccess && !hasAskedForStorage -> ScanGateDecision.Blocked(ScanGate.StoragePermission)
    hasStorageAccess -> ScanGateDecision.Run(ScanCoverage.InstalledAppsAndFiles)
    else -> ScanGateDecision.Run(ScanCoverage.InstalledAppsOnly)
}
