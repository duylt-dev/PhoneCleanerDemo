package com.pion.phonecleaner.feature.network.speedtestresult

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4).
 *
 * `onNavigateToTest` is what `:app` binds back to the `SpeedTest` route. This composable does not
 * name it: a direct edge from one feature module to another is what `LLM.md` §2 forbids, and the
 * whole point of the callback is that the edge lives in the `NavHost`.
 */
@Composable
fun SpeedTestResultRoute(
    onNavigateToTest: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SpeedTestResultViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SpeedTestResultEffect.NavigateBack -> onNavigateBack()
            SpeedTestResultEffect.NavigateToTest -> onNavigateToTest()
        }
    }

    BackHandler { onIntent(SpeedTestResultIntent.BackPressed) }

    SpeedTestResultScreen(state = state, onIntent = onIntent, modifier = modifier)
}
