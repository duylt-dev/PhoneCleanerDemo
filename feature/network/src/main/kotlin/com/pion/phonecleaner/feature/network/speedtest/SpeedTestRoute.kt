package com.pion.phonecleaner.feature.network.speedtest

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1) and
 * never a `NavController`: `onNavigateToResult` is what `:app` binds to the `SpeedTestResult` route,
 * popping this one on the way (`popUpTo(SpeedTest) { inclusive = true }` — what the competitor's
 * `finish()` did).
 *
 * `BackHandler` routes the system back gesture through the same intent as the header, so the abandon
 * prompt cannot be bypassed by one of the two. The competitor swallows `onBackPressed` entirely.
 */
@Composable
fun SpeedTestRoute(
    onNavigateToResult: (downloadBps: Long, uploadBps: Long) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpeedTestViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is SpeedTestEffect.NavigateToResult ->
                onNavigateToResult(effect.downloadBps, effect.uploadBps)

            SpeedTestEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(SpeedTestIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(SpeedTestIntent.BackPressed) }

    SpeedTestScreen(state = state, onIntent = onIntent, modifier = modifier)
}
