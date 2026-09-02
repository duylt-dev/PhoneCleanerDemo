package com.pion.phonecleaner.feature.applock.lockscreen

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.feature.applock.component.SecureScreen
import com.pion.phonecleaner.feature.applock.component.rememberPinShake
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out — and here that is load-bearing rather than conventional.
 *
 * This composable is hosted by `LockScreenActivity` in `:app` (`LLM.md` §7.5), not by the `NavHost`.
 * [onUnlocked] is that Activity finishing so the guarded app comes back to the foreground;
 * [onGoHome] is it finishing to the launcher. **Neither is "back" into the app being guarded**,
 * which is why this screen exists separately from `pin` at all.
 *
 * The lock surface itself is `docs/system-architecture.md` §10.1 **P6**, an open owner decision: an
 * Activity raised from a background poll is restricted from Android 10 and largely blocked on 14,
 * and the alternative — a `TYPE_APPLICATION_OVERLAY` `ComposeView` owned by a lifecycle-aware
 * service — re-introduces `SYSTEM_ALERT_WINDOW`. This file is the *content*, and it is the same
 * content either way; nothing here assumes which host wins.
 */
@Composable
fun LockScreenRoute(
    onUnlocked: () -> Unit,
    onGoHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LockScreenViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val scope = rememberCoroutineScope()
    val shake = rememberPinShake()

    // The pad and the recents thumbnail stop being screenshottable while this is on screen.
    SecureScreen()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            LockScreenEffect.Unlock -> onUnlocked()
            LockScreenEffect.GoHome -> onGoHome()
            LockScreenEffect.ShakeKeypad -> scope.launch { shake.play() }
        }
    }

    // Back goes to the launcher, not into the guarded app. Actually correct in the competitor, and
    // deliberately kept (§3.5) — but as an Effect, not as a flag in state.
    BackHandler { onIntent(LockScreenIntent.BackPressed) }

    LockScreenScreen(state = state, onIntent = onIntent, modifier = modifier, shakeOffset = shake.offset)
}
