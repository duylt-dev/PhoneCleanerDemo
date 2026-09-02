package com.pion.phonecleaner.feature.notification.hiddenlist

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.feature.notification.component.launchApp
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). The `Context` call for `LaunchApp` lives here, never in the
 * ViewModel (`docs/screens/17-notification-and-permissions.md` §3.3).
 *
 * `BackHandler` reproduces the modal block during a clear without an `onBackPressed` override, and
 * without the competitor's toast-on-a-blocked-Back destination.
 */
@Composable
fun HiddenNotificationsRoute(
    onNavigateBack: () -> Unit,
    onNavigateToCleanResult: (Int) -> Unit,
    onOpenHidingSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HiddenNotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HiddenNotificationsEffect.LaunchApp -> launchApp(context, effect.packageName)
            is HiddenNotificationsEffect.NavigateToCleanResult ->
                onNavigateToCleanResult(effect.clearedCount)

            HiddenNotificationsEffect.NavigateToHidingSettings -> onOpenHidingSettings()
            HiddenNotificationsEffect.NavigateBack -> onNavigateBack()
            // The message comes from the effect's own payload, never from `state.error` (MVI §4).
            is HiddenNotificationsEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }

            HiddenNotificationsEffect.ShowClearInProgressMessage -> scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(
                        com.pion.phonecleaner.feature.notification.R.string
                            .hidden_notifications_clear_in_progress,
                    ),
                )
            }
        }
    }

    BackHandler { onIntent(HiddenNotificationsIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        HiddenNotificationsScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
