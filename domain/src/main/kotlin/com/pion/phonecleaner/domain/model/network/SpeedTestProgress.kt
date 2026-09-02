package com.pion.phonecleaner.domain.model.network

import com.pion.phonecleaner.core.common.error.AppError

/**
 * What a connection measurement reports, in the house progress shape (`ScanProgress`,
 * `CleanProgress`, `SecurityScanPhase` — all the same idea).
 *
 * [NotConfigured] is the arm that makes this cluster honest, and it is the reason the flow is not a
 * plain `Flow<SpeedSample>`.
 *
 * > **PENDING OWNER DECISION 2.** A measurement needs a byte source — a host, a payload size and a
 * > ramp-up policy. No report in the corpus designs one, there is no backend in scope, and this is
 * > procurement rather than design (`docs/screens/19-network-and-speed-test.md` §2, §5 item 1). Until
 * > it is settled the shipped implementation emits [NotConfigured] and nothing else.
 *
 * A flow that simply completed without emitting would leave the caller to infer "nothing happened",
 * and the natural rendering of that inference is a zero. A zero is a number, and a number nobody
 * measured is exactly what this cluster must not put on screen. [NotConfigured] says so out loud, so
 * the screen renders a sentence instead.
 */
sealed interface SpeedTestProgress {

    /** No byte source is configured. Nothing was measured, and no figure is available. */
    data object NotConfigured : SpeedTestProgress

    data class Running(val sample: SpeedSample) : SpeedTestProgress

    data class Finished(val result: SpeedResult) : SpeedTestProgress

    data class Failed(val error: AppError) : SpeedTestProgress
}
