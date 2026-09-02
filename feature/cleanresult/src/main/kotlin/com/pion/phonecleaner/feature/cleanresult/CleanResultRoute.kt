package com.pion.phonecleaner.feature.cleanresult

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route — that is what makes
 * `:feature:A -> :feature:B` unnecessary rather than merely forbidden (`LLM.md` §2, §7.1).
 *
 * [summary] arrives as a navigation argument that `:app` unwraps from its `@Serializable` route type
 * and is handed to the ViewModel as a Koin parameter; this module cannot name that route type
 * (`LLM.md` §7.2).
 *
 * `BackHandler` is present and raises the ordinary intent. The competitor blocks Back here with a
 * toast and offers no arrow, which is what a destination does when its own navigation cannot survive
 * being left early.
 */
@Composable
fun CleanResultRoute(
    summary: CleanupSummary,
    onNavigateToFeature: (FeatureId) -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CleanResultViewModel = koinViewModel { parametersOf(summary) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is CleanResultEffect.NavigateToFeature -> onNavigateToFeature(effect.feature)
            CleanResultEffect.NavigateHome -> onNavigateHome()
            CleanResultEffect.NavigateBack -> onNavigateBack()
        }
    }

    BackHandler { onIntent(CleanResultIntent.BackPressed) }

    CleanResultScreen(state = state, onIntent = onIntent, modifier = modifier)
}
