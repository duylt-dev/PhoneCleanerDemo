package com.pion.phonecleaner.domain.model.security

import kotlinx.collections.immutable.ImmutableList

/**
 * Where one scan has got to — **a value, not a driver** (`docs/screens/15-antivirus.md` §0.2).
 *
 * The competitor's equivalent sealed class drives behaviour: the UI reacts to one of its states by
 * starting a five-minute synthetic progress animation and to another by writing a `ProgressBar`
 * directly, so the state machine cannot be unit-tested at all
 * (`docs/reverse-engineering/15-antivirus.md` §3.2). Nothing here starts anything.
 *
 * **`SecurityScanPhase`, not `ScanPhase`.** Four incompatible types were proposed under that one
 * name across four clusters, and the short name is retired rather than awarded
 * (`docs/system-architecture.md` §4.5). `:core:mvi`'s `ToolPhase` is a *different* type and this
 * module never imports it.
 */
sealed interface SecurityScanPhase {

    /** Nothing has started. The seed, and — unlike the competitor's — a state a screen can render. */
    data object Idle : SecurityScanPhase

    /** Enumerating. No counts exist yet, so progress is genuinely indeterminate. */
    data object Preparing : SecurityScanPhase

    /**
     * [current] of [total] apps submitted, [label] being the one in flight.
     *
     * The competitor animates 0 → 50 % over 300 000 ms *before* mapping the real counts onto
     * 50 → 100 %, so its bar is a fiction for up to five minutes and then jumps to half full
     * (§1.5). These two numbers are the only honest source, and the screen maps them onto the whole
     * range.
     */
    data class Scanning(val current: Int, val total: Int, val label: String) : SecurityScanPhase

    /** The scan completed. [findings] is already filtered by the repository (§0.4). */
    data class Finished(val findings: ImmutableList<ThreatVerdict>) : SecurityScanPhase

    /**
     * The scan stopped without a result.
     *
     * A **rendered state**, not the competitor's one-shot event: its own handling of that event is a
     * modal that replaces the screen, so it was never one-shot in the first place (§1.1).
     */
    data class Failed(val reason: ScanFailure) : SecurityScanPhase
}
