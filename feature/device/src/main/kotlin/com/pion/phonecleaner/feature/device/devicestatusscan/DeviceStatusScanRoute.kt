package com.pion.phonecleaner.feature.device.devicestatusscan

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
 * `devicestatusscan` — callbacks in, nothing out (MVI §4). It never names another feature's route
 * (`LLM.md` §2, §7.1).
 *
 * `onScanned` is wired by `:app` as `popUpTo(self) { inclusive = true }` (§8), so Back from the
 * detail returns to wherever the user came from rather than replaying the scan — which is exactly
 * what the competitor's `startActivity` + `finish()` pair does.
 *
 * `BackHandler` → intent → effect, against an `onBackPressed()` override plus a `Toast`: a `Toast`
 * outlives the screen that raised it and can land over whatever came next.
 */
@Composable
fun DeviceStatusScanRoute(
    onScanned: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceStatusScanViewModel = koinViewModel(),
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
            DeviceStatusScanEffect.NavigateToDetail -> onScanned()
            DeviceStatusScanEffect.NavigateBack -> onNavigateBack()
            DeviceStatusScanEffect.ShowScanInProgressMessage -> scope.launch {
                snackbarHostState.showSnackbar(inProgressMessage)
            }
        }
    }

    BackHandler { onIntent(DeviceStatusScanIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        DeviceStatusScanScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
