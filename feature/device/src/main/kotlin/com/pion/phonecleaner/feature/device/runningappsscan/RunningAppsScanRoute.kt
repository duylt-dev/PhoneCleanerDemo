package com.pion.phonecleaner.feature.device.runningappsscan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.feature.device.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * `runningappsscan` — callbacks in, nothing out (MVI §4). It never names another feature's route
 * (`LLM.md` §2, §7.1).
 *
 * `onScanned` is wired by `:app` as `popUpTo(self) { inclusive = true }` (§8).
 *
 * **Navigation never depends on an ad callback** (`LLM.md` §7): the competitor routes this screen's
 * onward navigation through a facade that is a no-op when its static Activity handle is null, which
 * makes the next screen unreachable for that run. Here the effect is raised by the completion of the
 * work and by nothing else.
 */
@Composable
fun RunningAppsScanRoute(
    onScanned: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningAppsScanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Held steady: the subtree below animates a ring at 60 Hz, which is the "costs more than a
    // layout pass" case MVI §8 keeps this line for.
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val inProgressMessage = stringResource(R.string.device_scan_in_progress)

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            RunningAppsScanEffect.NavigateToRunningApps -> onScanned()
            RunningAppsScanEffect.NavigateBack -> onNavigateBack()
            RunningAppsScanEffect.ShowScanInProgressMessage -> scope.launch {
                snackbarHostState.showSnackbar(inProgressMessage)
            }
        }
    }

    BackHandler { onIntent(RunningAppsScanIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        RunningAppsScanScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
