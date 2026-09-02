package com.pion.phonecleaner.data.network

import com.pion.phonecleaner.domain.model.network.SpeedTestProgress
import com.pion.phonecleaner.domain.repository.SpeedTestRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * The only `SpeedTestRepository` that ships today. **It opens no socket, transfers no bytes and
 * reports no number.**
 *
 * ### Why this class exists instead of a measurement
 *
 * PENDING OWNER DECISION 2 (`docs/screens/19-network-and-speed-test.md` §2, §5 item 1;
 * `docs/system-architecture.md` §10.1 P2). A real measurement needs a byte source — a host, a payload
 * size and a ramp-up policy. **No report in the corpus designs one, and there is no backend in
 * scope.** That is procurement, not design, and it is not this cluster's to settle.
 *
 * ### Why it does not fall back to the competitor's mechanism
 *
 * The competitor's speed test transfers nothing: it reads a device-wide `TrafficStats` delta and
 * divides it by a wall clock that includes an interstitial's dwell time, with its own ad preload
 * issued *before* the baseline is stamped (chapter §4.2). That produces a plausible-looking figure
 * from other apps' traffic. Shipping it would be a fabricated performance number presented as a
 * measurement, which is precisely what `LLM.md` §1 forbids and what this project exists not to build.
 *
 * ### What replaces it
 *
 * [SpeedTestProgress.NotConfigured], once, and then completion. The screen renders that as a
 * sentence. It never renders a zero, because a zero is a figure and no figure was measured.
 *
 * ### What changes when the decision lands
 *
 * If a host is procured, a sibling class named for its mechanism replaces the one line in
 * `networkDataModule` and nothing above it moves. If the answer is "no host", this file, the two
 * speed-test screens and that line are deleted together and the traffic half is untouched.
 *
 * There is **no networking dependency in any build file** for this cluster. The version catalogue
 * notes OkHttp as "to be added only if the speed test ships"; adding it now would declare a decision
 * that has not been made.
 */
internal class UnconfiguredSpeedTestRepository : SpeedTestRepository {

    override fun measure(): Flow<SpeedTestProgress> = flowOf(SpeedTestProgress.NotConfigured)
}
