package com.pion.phonecleaner.feature.notification.hidingsettings

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
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4).
 *
 * Back **pops**. The competitor's Back forward-navigates and calls its router before `super`, so both
 * transitions play; and it finishes another screen by simple class name through a global Activity
 * registry (`zd.c.e("SecuitancActivity")`). Neither has a port — the back stack owns lifetimes
 * (`docs/screens/17-notification-and-permissions.md` §2.5).
 */
@Composable
fun NotificationHidingSettingsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationHidingSettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            NotificationHidingSettingsEffect.NavigateBack -> onNavigateBack()
            is NotificationHidingSettingsEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    BackHandler { onIntent(NotificationHidingSettingsIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        NotificationHidingSettingsScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
