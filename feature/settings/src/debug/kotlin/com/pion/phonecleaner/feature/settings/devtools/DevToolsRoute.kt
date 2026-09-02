package com.pion.phonecleaner.feature.settings.devtools

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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.feature.settings.R
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (`LLM.md` §7.1). **`src/debug` only** — this destination does not exist
 * in a release build, which is why `AboutViewModel` also guards its intent
 * (`docs/screens/20-settings-language-and-push.md` §3.2, §6.4 delta 1).
 *
 * The copy for every message arm is resolved here: the ViewModel names an outcome, the Route says it
 * in words (MVI §5).
 */
@Composable
fun DevToolsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DevToolsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    CollectEffects(viewModel.effects) { effect ->
        val message = when (effect) {
            is DevToolsEffect.Delivered ->
                context.getString(R.string.settings_devtools_push_delivered, effect.dataKeyCount)

            DevToolsEffect.NothingToDeliver ->
                context.getString(R.string.settings_devtools_push_invalid)

            DevToolsEffect.DeliveryFailed ->
                context.getString(R.string.settings_devtools_push_failed)

            DevToolsEffect.NavigateBack -> {
                onNavigateBack()
                null
            }
        }
        // Not awaited: showSnackbar suspends until dismissal, which would queue the next effect.
        if (message != null) scope.launch { snackbarHostState.showSnackbar(message) }
    }

    BackHandler { onIntent(DevToolsIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        DevToolsScreen(state = state, onIntent = onIntent)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PageSpacing.snackbarLift),
        )
    }
}
