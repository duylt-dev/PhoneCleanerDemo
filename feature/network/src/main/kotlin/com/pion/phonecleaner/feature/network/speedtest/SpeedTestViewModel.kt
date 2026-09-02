package com.pion.phonecleaner.feature.network.speedtest

import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.network.SpeedResult
import com.pion.phonecleaner.domain.model.network.SpeedTestProgress
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.RunSpeedTestUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `speedtest` (`docs/screens/19-network-and-speed-test.md` §2.2).
 *
 * **The ViewModel never sees a socket.** All the work is behind `RunSpeedTestUseCase` →
 * `SpeedTestRepository`, which is what keeps the open owner decision contained: settling the byte
 * source changes one implementation class and nothing here.
 *
 * Every path leaves [SpeedTestState.Phase.Running]: a sample, a completion, a `NotConfigured`, a
 * failure, a timeout and `onError` all land on a terminal phase, so the indicator cannot strand.
 */
class SpeedTestViewModel(
    private val runSpeedTest: RunSpeedTestUseCase,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger,
) : MviViewModel<SpeedTestState, SpeedTestIntent, SpeedTestEffect>(SpeedTestState(), log) {

    private var testJob: Job? = null

    override fun onIntent(intent: SpeedTestIntent) {
        when (intent) {
            // Idempotent: it arrives on every ON_START, and only the first one starts a run.
            SpeedTestIntent.ScreenStarted ->
                if (currentState.phase == SpeedTestState.Phase.Idle) start()

            SpeedTestIntent.BackPressed -> onBackPressed()
            SpeedTestIntent.AbandonConfirmed -> onAbandonConfirmed()
            SpeedTestIntent.AbandonDismissed ->
                setState { copy(isAbandonPromptVisible = false) }

            SpeedTestIntent.RetryPressed -> start()
        }
    }

    /**
     * Back while a measurement runs asks first; the prompt is a Boolean on state, so a rotation
     * keeps it. The competitor calls a toast helper straight out of `onBackPressed`, which is the
     * Activity authoring user-facing copy.
     */
    private fun onBackPressed() {
        if (currentState.isRunning) {
            setState { copy(isAbandonPromptVisible = true) }
        } else {
            sendEffect(SpeedTestEffect.NavigateBack)
        }
    }

    private fun onAbandonConfirmed() {
        testJob?.cancel()
        setState { copy(phase = SpeedTestState.Phase.Idle, isAbandonPromptVisible = false) }
        sendEffect(SpeedTestEffect.NavigateBack)
    }

    private fun start() {
        if (currentState.isRunning) return // re-entry guard, in the reducer
        testJob?.cancel()
        setState { withStarted() }
        testJob = launchSafely(onError = { setState { withFailure(it) } }) {
            var result: SpeedResult? = null
            var notConfigured = false

            val completed = withTimeoutOrNull(TestTimeoutMillis) {
                runSpeedTest().collect { progress ->
                    when (progress) {
                        SpeedTestProgress.NotConfigured -> notConfigured = true
                        is SpeedTestProgress.Running -> setState { withSample(progress.sample) }
                        is SpeedTestProgress.Finished -> result = progress.result
                        is SpeedTestProgress.Failed -> setState { withFailure(progress.error) }
                    }
                }
                true
            } == true

            settle(completed = completed, result = result, notConfigured = notConfigured)
        }
    }

    /**
     * One place decides how a run ended, so no branch can forget to lower the phase.
     *
     * `notConfigured` wins over everything else: it is not this run failing, it is there being
     * nothing to run, and the screen says exactly that rather than reporting a figure.
     */
    private fun settle(completed: Boolean, result: SpeedResult?, notConfigured: Boolean) {
        when {
            notConfigured -> setState { withNotConfigured() }
            !completed -> setState { withFailure(AppError.Unexpected(TIMEOUT_DETAIL)) }
            result != null -> onMeasured(result)
            currentState.error != null -> Unit // a Failed arm already settled the phase
            else -> setState { withFailure(AppError.Unexpected(NO_RESULT_DETAIL)) }
        }
    }

    private fun onMeasured(result: SpeedResult) {
        setState { withFinished() }
        launchSafely { markFeatureUsed(FeatureId.NetworkTest) } // bookkeeping: fire and forget
        sendEffect(
            SpeedTestEffect.NavigateToResult(
                downloadBps = result.downloadBytesPerSecond,
                uploadBps = result.uploadBytesPerSecond,
            ),
        )
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }

    private companion object {
        /** A bounded wait. The competitor bounds nothing on any screen in this cluster (§2.4 D-3). */
        const val TestTimeoutMillis = 60_000L

        /**
         * `AppError` has no `Timeout` arm and `core/common/error/AppError.kt` belongs to another
         * owner, so these details reach the log and the user-facing string is the generic one.
         */
        const val TIMEOUT_DETAIL = "speed test exceeded ${TestTimeoutMillis}ms"
        const val NO_RESULT_DETAIL = "speed test finished without a result"
    }
}
