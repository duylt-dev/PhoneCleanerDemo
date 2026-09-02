package com.pion.phonecleaner.feature.notification.gate

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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.feature.notification.component.openNotificationListenerSettings
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1).
 *
 * [LifecycleStartEffect] is the replacement for a permission callback: there is none for
 * `BIND_NOTIFICATION_LISTENER_SERVICE`, and the grant can only be observed by re-reading on start. The
 * competitor routes on an XXPermissions callback that ignores `allGranted`, so a decline is routed as
 * an accept (§1.5).
 *
 * Both competitor walls are `launchMode="singleTask"`, which on a leaf screen collapses the back stack
 * of whatever launched it. This is a normal destination in one Activity.
 */
@Composable
fun NotificationGateRoute(
    onNavigateBack: () -> Unit,
    onNavigateToHiddenList: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationGateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            NotificationGateEffect.OpenNotificationListenerSettings ->
                openNotificationListenerSettings(context)

            NotificationGateEffect.NavigateToHiddenList -> onNavigateToHiddenList()
            NotificationGateEffect.NavigateBack -> onNavigateBack()
            // The message comes from the effect's own payload, never from `state.error`: the collector
            // runs one main-queue turn after `sendEffect` and a frame before the matching `setState`
            // renders, so reading state here reads the pre-failure value (MVI §4).
            is NotificationGateEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(NotificationGateIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(NotificationGateIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        NotificationGateScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
