package com.pion.phonecleaner.feature.onboarding.devicecheck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Public and stateful; one navigation callback in, nothing out (MVI §4). It never names another
 * feature's route (`LLM.md` §2, §7.1).
 *
 * `:app` reaches this from `SplashEffect.NavigateToDeviceCheck` with
 * `popUpTo(Splash) { inclusive = true }`, and leaves it with `popUpTo(0)` — which is what `finish()`
 * did at `CucurtagActivity.java:188`. The competitor's back stack is a hand-rolled
 * `ArrayList<Activity>` in `zd.c`; it is deleted, not ported.
 *
 * UNKNOWN — the header animation. `sketcrat.xml` plays `assets/lt/scan_device/data.json` (16 961 B)
 * plus four PNGs at ratio 1:0.65. Looked for, and not found: any Lottie asset in this project
 * (`:core:ui/src/main/res` holds `values/strings.xml` and nothing else; there is no `assets/`
 * directory in any module), and `lottie-compose` is in `gradle/libs.versions.toml` but declared by
 * no module. Adding the dependency for an asset that does not exist would be a stub that reads as
 * wired, so the screen ships without it. **The asset belongs in `:feature:onboarding/src/main/assets/`
 * with `implementation(libs.lottie.compose)` in this module's build file**, and it is decoration:
 * `LLM.md` §12 keeps Lottie off the data path, so nothing above waits on it.
 */
@Composable
fun DeviceCheckRoute(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceCheckViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            DeviceCheckEffect.NavigateToHome -> onNavigateToHome()
            // `thirlea.smoothScrollToPosition(index)`. The scroll is an Effect and not a field on
            // state because it is one-shot: held on state, a rotation would replay it and drag the
            // list back to a row the user has scrolled past.
            // The scroll suspends, so it runs in the screen's own scope: holding the collector open
            // for the length of an animation would stall every effect queued behind it.
            is DeviceCheckEffect.ScrollToRow -> scope.launch {
                listState.animateScrollToItem(effect.index)
            }
        }
    }

    // `Y()`'s lifecycle half: the sequence's clock stops while the screen is not resumed. Platform
    // state lives in the composable and reports upward (MVI §4).
    LifecycleResumeEffect(viewModel) {
        onIntent(DeviceCheckIntent.ScreenResumed)
        onPauseOrDispose { onIntent(DeviceCheckIntent.ScreenPaused) }
    }

    // Always intercepted, and the REDUCER decides what it means: nothing while the sequence runs,
    // the CTA's own action once the CTA is live (MVI §3 — guard re-entrancy in the reducer, not in
    // the UI). The competitor implements that same rule twice, in `e()` and in `onKeyDown`.
    BackHandler(enabled = true) { onIntent(DeviceCheckIntent.BackPressed) }

    DeviceCheckScreen(
        state = state,
        onIntent = onIntent,
        listState = listState,
        modifier = modifier,
    )
}
