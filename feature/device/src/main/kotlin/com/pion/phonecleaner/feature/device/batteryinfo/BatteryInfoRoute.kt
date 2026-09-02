package com.pion.phonecleaner.feature.device.batteryinfo

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * `batteryinfo` — callbacks in, nothing out (MVI §4). It never names another feature's route
 * (`LLM.md` §2, §7.1).
 *
 * **Argument-free**, because the seed from `batteryscan` arrives through `DeviceScanSessionStore`,
 * not through a route argument or a Koin parameter — a Koin parameter cannot cross a `NavHost` edge
 * (§0.2, `docs/system-architecture.md` §10.3 **U1**).
 *
 * `collectAsStateWithLifecycle` is what stops and restarts the battery `callbackFlow` at `STARTED`,
 * which is the whole reason this screen needs no `ScreenResumed` intent and no lifecycle overrides.
 */
@Composable
fun BatteryInfoRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BatteryInfoViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            BatteryInfoEffect.NavigateBack -> onNavigateBack()
        }
    }

    BackHandler { onIntent(BatteryInfoIntent.BackPressed) }

    BatteryInfoScreen(state = state, onIntent = onIntent, modifier = modifier)
}
