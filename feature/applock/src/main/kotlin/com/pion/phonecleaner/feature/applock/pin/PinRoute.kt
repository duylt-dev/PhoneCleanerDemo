package com.pion.phonecleaner.feature.applock.pin

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
import com.pion.phonecleaner.feature.applock.component.SecureScreen
import com.pion.phonecleaner.feature.applock.component.rememberPinShake
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (`LLM.md` §7.1). The mode arrives as the route argument and is read once,
 * in the ViewModel's constructor; this composable never sees it and never sees a digit.
 *
 * [onNavigateToAppLock] is what `Set` and `Verify` succeed into; `Change` succeeds into
 * [onNavigateBack]. Which route each of those is belongs to the `NavHost` in `:app`.
 */
@Composable
fun PinRoute(
    onNavigateToAppLock: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shake = rememberPinShake()

    // The pad and the recents thumbnail stop being screenshottable while this is on screen.
    SecureScreen()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            PinEffect.NavigateToAppLock -> onNavigateToAppLock()
            PinEffect.NavigateBack -> onNavigateBack()
            // A one-shot: played here, never stored, so a rotation cannot replay it.
            PinEffect.ShakeKeypad -> scope.launch { shake.play() }
            // Resolved from the EFFECT'S OWN payload: the collector runs one main-queue turn after
            // sendEffect and a frame before the matching setState renders (MVI §4).
            is PinEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    BackHandler { onIntent(PinIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        PinScreen(state = state, onIntent = onIntent, shakeOffset = shake.offset)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PageSpacing.snackbarLift),
        )
    }
}
