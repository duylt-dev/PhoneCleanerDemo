package com.pion.phonecleaner.feature.network.speedtest

import androidx.compose.runtime.Immutable
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.mvi.UiEffect
import com.pion.phonecleaner.core.mvi.UiIntent
import com.pion.phonecleaner.core.mvi.UiState
import com.pion.phonecleaner.domain.model.network.SpeedTestStage

/**
 * `speedtest` (`docs/screens/19-network-and-speed-test.md` §2.1).
 *
 * > **PENDING OWNER DECISION 2, and it is not settled by this file.** A measurement needs a byte
 * > source; none exists in the corpus and there is no backend in scope, so this contract is written
 * > against `SpeedTestRepository` and stops there. No endpoint, payload size, chunk count or discard
 * > fraction is named anywhere in this cluster.
 *
 * [Phase.NotConfigured] is the state the shipped implementation actually reaches, and the screen
 * renders it as its honest default. It is a distinct phase and not `Finished` with a zero, because
 * a zero is a figure and no figure was measured.
 *
 * The competitor's six Activity fields collapse here: its `progressAnimator` becomes
 * [progressPercent], its `scanning` flag becomes [phase], its `hasShow` disappears with the advert,
 * and its three `TrafficStats` baselines have no analogue at all — they are the mechanism that
 * produces its fabricated figure, and they belong to no layer of this design.
 */
@Immutable
data class SpeedTestState(
    val phase: Phase = Phase.Idle,

    /** Position in the measurement, `0..100`. Reported by the measurement, never by an animator. */
    val progressPercent: Int = 0,

    /** The live figure while the test runs. `null` until the first sample, and never rendered as 0. */
    val currentBytesPerSecond: Long? = null,

    val stage: SpeedTestStage = SpeedTestStage.Download,

    /** A visible condition, so a Boolean on state — never an Effect (`LLM.md` §7.4). */
    val isAbandonPromptVisible: Boolean = false,

    val error: AppError? = null,
) : UiState {

    enum class Phase {
        Idle,
        Running,

        /** No byte source is configured. Terminal, and not a failure of this run. */
        NotConfigured,
        Finished,
    }

    val isRunning: Boolean get() = phase == Phase.Running

    /** A retry can only help a run that failed. Nothing retries an absent byte source. */
    val canRetry: Boolean get() = phase == Phase.Finished && error != null
}

sealed interface SpeedTestIntent : UiIntent {
    data object ScreenStarted : SpeedTestIntent
    data object BackPressed : SpeedTestIntent
    data object AbandonConfirmed : SpeedTestIntent
    data object AbandonDismissed : SpeedTestIntent
    data object RetryPressed : SpeedTestIntent
}

sealed interface SpeedTestEffect : UiEffect {
    /**
     * Carries the payload. The handler must never read it back off state: the collector runs one
     * main-queue turn after `sendEffect` and a frame before the matching `setState` renders, so
     * reading state there reads the pre-completion value (MVI §4 — a shipped bug elsewhere).
     */
    data class NavigateToResult(val downloadBps: Long, val uploadBps: Long) : SpeedTestEffect
    data object NavigateBack : SpeedTestEffect
}
