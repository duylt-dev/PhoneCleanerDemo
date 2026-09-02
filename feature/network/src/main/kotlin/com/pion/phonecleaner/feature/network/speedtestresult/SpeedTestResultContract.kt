package com.pion.phonecleaner.feature.network.speedtestresult

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState

/**
 * `speedtestresult` (`docs/screens/19-network-and-speed-test.md` §3.1).
 *
 * Two numbers arrive as **route arguments** and are read into the initial state in the constructor —
 * not copied in a frame later (MVI §3).
 *
 * [isMeasured] exists because both arguments default to `0L`, which is what a route entered without
 * a completed measurement carries. Rendering that as `0 B/s` would present an unmeasured figure as a
 * measurement, so the screen says nothing was recorded instead. PENDING OWNER DECISION 2 makes that
 * the reachable state today; it is a rendering decision, and it settles nothing about the decision.
 */
@Immutable
data class SpeedTestResultState(
    val downloadBytesPerSecond: Long = 0L,
    val uploadBytesPerSecond: Long = 0L,
) : UiState {
    val isMeasured: Boolean get() = downloadBytesPerSecond > 0L || uploadBytesPerSecond > 0L
}

sealed interface SpeedTestResultIntent : UiIntent {
    /** The competitor's "GOT IT". */
    data object DonePressed : SpeedTestResultIntent
    data object BackPressed : SpeedTestResultIntent

    /** New here: the competitor's result screen cannot repeat the test (§2.4 D-3). */
    data object RunAgainPressed : SpeedTestResultIntent
}

sealed interface SpeedTestResultEffect : UiEffect {
    data object NavigateBack : SpeedTestResultEffect
    data object NavigateToTest : SpeedTestResultEffect
}
