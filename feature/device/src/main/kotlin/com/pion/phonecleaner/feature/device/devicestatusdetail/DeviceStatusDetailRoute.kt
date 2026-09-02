package com.pion.phonecleaner.feature.device.devicestatusdetail

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * `devicestatusdetail` — callbacks in, nothing out (MVI §4).
 *
 * The three *Check* callbacks are what keep `:feature:device` free of an edge to `:feature:junk`:
 * the storage card's Check is a junk-clean navigation that `:app` wires, and this module never names
 * that route (`LLM.md` §2, §7.1).
 *
 * `LifecycleResumeEffect` raises `ScreenResumed` on every `ON_RESUME`, which is what makes the page
 * correct after a trip to the junk cleaner. The competitor's page is a snapshot taken once, in
 * `onCreate`, and nothing refreshes it.
 */
@Composable
fun DeviceStatusDetailRoute(
    onNavigateToRunningApps: () -> Unit,
    onNavigateToJunkClean: () -> Unit,
    onNavigateToBattery: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceStatusDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            DeviceStatusDetailEffect.NavigateToRunningApps -> onNavigateToRunningApps()
            DeviceStatusDetailEffect.NavigateToJunkClean -> onNavigateToJunkClean()
            DeviceStatusDetailEffect.NavigateToBattery -> onNavigateToBattery()
            DeviceStatusDetailEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleResumeEffect(viewModel) {
        onIntent(DeviceStatusDetailIntent.ScreenResumed)
        onPauseOrDispose { }
    }

    BackHandler { onIntent(DeviceStatusDetailIntent.BackPressed) }

    DeviceStatusDetailScreen(state = state, onIntent = onIntent, modifier = modifier)
}
