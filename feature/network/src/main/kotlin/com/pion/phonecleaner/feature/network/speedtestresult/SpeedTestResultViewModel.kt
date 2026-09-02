package com.pion.phonecleaner.feature.network.speedtestresult

import androidx.lifecycle.SavedStateHandle
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.mvi.MviViewModel
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase

/**
 * `speedtestresult` (`docs/screens/19-network-and-speed-test.md` §3.2).
 *
 * This is close to MVI §3's "a screen with no ViewModel at all" exemption — it renders two numbers
 * and three links. It keeps one because it has exactly one decision: recording the feature use. The
 * moment a screen acquires a decision it acquires a ViewModel, in the same change.
 *
 * `savedState` is a constructor parameter and **not** a `private val`: it is read once, to build the
 * initial state, and nothing else on this screen writes to it.
 *
 * ROUTE ARGUMENTS — the keys below are the property names of the `@Serializable` route declared in
 * `:app/navigation/Routes.kt`, which this cluster does not own. The exact declaration this screen
 * needs is reported rather than written:
 * `@Serializable data class SpeedTestResult(val downloadBps: Long = 0L, val uploadBps: Long = 0L)`.
 * Both default to `0L`, registered with the **same** defaults in every shell, so resizing the window
 * does not clear the back stack.
 */
class SpeedTestResultViewModel(
    savedState: SavedStateHandle,
    private val markFeatureUsed: MarkFeatureUsedUseCase,
    log: AppLogger,
) : MviViewModel<SpeedTestResultState, SpeedTestResultIntent, SpeedTestResultEffect>(
    // The arguments ARE the initial state.
    SpeedTestResultState(
        downloadBytesPerSecond = savedState[ARG_DOWNLOAD_BPS] ?: 0L,
        uploadBytesPerSecond = savedState[ARG_UPLOAD_BPS] ?: 0L,
    ),
    log,
) {

    init {
        launchSafely { markFeatureUsed(FeatureId.NetworkTest) }
    }

    override fun onIntent(intent: SpeedTestResultIntent) {
        when (intent) {
            SpeedTestResultIntent.DonePressed,
            SpeedTestResultIntent.BackPressed,
            -> sendEffect(SpeedTestResultEffect.NavigateBack)

            SpeedTestResultIntent.RunAgainPressed ->
                sendEffect(SpeedTestResultEffect.NavigateToTest)
        }
    }

    companion object {
        /** The `SavedStateHandle` keys — the route's own property names, nothing invented. */
        const val ARG_DOWNLOAD_BPS = "downloadBps"
        const val ARG_UPLOAD_BPS = "uploadBps"
    }
}
