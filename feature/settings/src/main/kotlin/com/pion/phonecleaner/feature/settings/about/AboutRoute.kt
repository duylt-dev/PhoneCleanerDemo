package com.pion.phonecleaner.feature.settings.about

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (`LLM.md` §7.1). It never names another feature's route.
 *
 * [onNavigateToDeveloperTools] is wired only in a debug build — the destination itself is compiled
 * into `src/debug` and does not exist in release (§6.4 delta 1) — but the parameter is not optional
 * here: a `NavHost` that has no such destination passes a no-op, and the ViewModel's own guard
 * already prevents the intent from being raised.
 */
@Composable
fun AboutRoute(
    onNavigateToLegalDocument: (LegalDocument) -> Unit,
    onNavigateToDeveloperTools: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AboutViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is AboutEffect.NavigateToLegalDocument -> onNavigateToLegalDocument(effect.document)
            AboutEffect.NavigateToDeveloperTools -> onNavigateToDeveloperTools()
            AboutEffect.NavigateBack -> onNavigateBack()
        }
    }

    BackHandler { onIntent(AboutIntent.BackPressed) }

    AboutScreen(state = state, onIntent = onIntent, modifier = modifier)
}
