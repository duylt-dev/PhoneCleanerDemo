package com.pion.phonecleaner.feature.applock.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.core.ui.token.PageSpacing
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (`LLM.md` §7.1). `:feature:applock` does not name a route; the `NavHost`
 * in `:app` decides that [onNavigateToChangePin] means `Pin(mode = PinMode.Change)`.
 */
@Composable
fun AppLockSettingsRoute(
    onNavigateToChangePin: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppLockSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Held steady once, so four rows below this lambda keep their skip (`LLM.md` §8).
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            AppLockSettingsEffect.NavigateToChangePin -> onNavigateToChangePin()
            AppLockSettingsEffect.NavigateBack -> onNavigateBack()
            // Resolved from the EFFECT'S OWN payload, never from state: the collector runs one
            // main-queue turn after sendEffect and a frame before the matching setState renders.
            // Not awaited: showSnackbar suspends until dismissal, which would queue the next effect.
            is AppLockSettingsEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    BackHandler { onIntent(AppLockSettingsIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        AppLockSettingsScreen(state = state, onIntent = onIntent)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PageSpacing.snackbarLift),
        )
    }
}
