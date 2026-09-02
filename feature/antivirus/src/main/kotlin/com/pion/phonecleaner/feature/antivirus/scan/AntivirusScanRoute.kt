package com.pion.phonecleaner.feature.antivirus.scan

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Public and stateful; navigation callbacks in, nothing out (MVI §4). It never names another
 * feature's route — that is what makes `:feature:A -> :feature:B` unnecessary rather than merely
 * forbidden (`LLM.md` §2, §7.1).
 *
 * Everything platform-shaped lives here and reports upward: the permission launcher, the two
 * connectivity/permission probes and the lifecycle edge. `AntivirusScanViewModel` imports no
 * `android.*` type.
 *
 * `LifecycleStartEffect` re-reports on **every** `ON_START`, which is the permission funnel of
 * `LLM.md` §7.4: the grant is given on a screen the user returns from, and the competitor's
 * equivalent check runs once and never again, so returning granted leaves the gate up.
 *
 * `CollectEffects` has a branch for every declared effect and no `else`, uses `collect` (never
 * `collectLatest`) and is lifecycle-aware — two navigations raised in one frame must not collapse.
 */
@Composable
fun AntivirusScanRoute(
    onNavigateToResult: (Int) -> Unit,
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AntivirusScanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Held steady: the subtree below animates a progress value continuously, which is the "costs
    // more than a layout pass" case MVI §8 keeps this line for.
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val storageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // The answer is read back from the platform rather than from the result map: a partial
        // media grant on Android 14 reports `false` for a permission the app can still read with.
        onIntent(
            AntivirusScanIntent.GateStateReported(
                hasStorageAccess = hasAnyStorageRead(context),
                isOnline = isOnline(context),
            ),
        )
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is AntivirusScanEffect.NavigateToResult -> onNavigateToResult(effect.findingCount)
            is AntivirusScanEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            AntivirusScanEffect.NavigateBack -> onNavigateBack()
            AntivirusScanEffect.RequestStorageAccess ->
                storageLauncher.launch(storageReadPermissions())

            is AntivirusScanEffect.OpenUrl -> uriHandler.openUri(effect.url)

            // The message comes from the effect's own payload, never from `state`: the collector
            // runs one main-queue turn after `sendEffect` and a frame before the matching
            // `setState` renders, so reading state here reads the pre-failure value (MVI §4).
            is AntivirusScanEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    LifecycleStartEffect(context, viewModel) {
        onIntent(AntivirusScanIntent.ScreenStarted)
        onIntent(
            AntivirusScanIntent.GateStateReported(
                hasStorageAccess = hasAnyStorageRead(context),
                isOnline = isOnline(context),
            ),
        )
        onStopOrDispose { }
    }

    // The gate is re-reported on every default-network change as well as on every ON_START; see
    // that file for the dead end the lifecycle edge alone leaves behind.
    NetworkStateEffect(onIntent)

    // Replaces an `onKeyDown` override that consumes BACK and never calls `super`. While the scan
    // runs this raises the stop confirm rather than leaving; the reducer decides which.
    BackHandler { onIntent(AntivirusScanIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        AntivirusScanScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
