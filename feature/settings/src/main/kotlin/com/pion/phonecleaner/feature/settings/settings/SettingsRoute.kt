package com.pion.phonecleaner.feature.settings.settings

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
 * Callbacks in, nothing out (`LLM.md` §7.1).
 *
 * There is **no global click debounce**. The competitor debounces every navigation in the app on one
 * process-global `static long` for 500 ms (`java/md/g1.java:61, 426-432`), so a tap on a row here
 * suppresses a tap anywhere else in the process — cross-screen coupling with no owner
 * (`docs/screens/20-settings-language-and-push.md` §1.4 delta 1). A per-`NavController`
 * duplicate-destination guard covers the double-navigate case, and it lives in `:app`.
 */
@Composable
fun SettingsRoute(
    onNavigateToLanguage: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToPermissionCentre: () -> Unit,
    onNavigateToTrash: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SettingsEffect.NavigateToLanguage -> onNavigateToLanguage()
            SettingsEffect.NavigateToAbout -> onNavigateToAbout()
            SettingsEffect.NavigateToPermissionCentre -> onNavigateToPermissionCentre()
            SettingsEffect.NavigateToTrash -> onNavigateToTrash()
            SettingsEffect.NavigateBack -> onNavigateBack()
        }
    }

    // Raised for symmetry with every other permission-aware screen (LLM.md §7.4). The reducer does
    // nothing with it: the three collectors already re-emit, and a second refresh path would be a
    // second source of truth.
    LifecycleResumeEffect(viewModel) {
        onIntent(SettingsIntent.ScreenResumed)
        onPauseOrDispose { }
    }

    BackHandler { onIntent(SettingsIntent.BackPressed) }

    SettingsScreen(state = state, onIntent = onIntent, modifier = modifier)
}
