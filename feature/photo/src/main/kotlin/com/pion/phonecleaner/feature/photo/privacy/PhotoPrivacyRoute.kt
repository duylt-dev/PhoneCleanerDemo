package com.pion.phonecleaner.feature.photo.privacy

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import org.koin.androidx.compose.koinViewModel

/** `docs/screens/13-photo-and-media.md` §5.3. Callbacks in, nothing out. */
@Composable
fun PhotoPrivacyRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoPrivacyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PhotoPrivacyEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            PhotoPrivacyEffect.NavigateBack -> onNavigateBack()
        }
    }

    LaunchedEffect(viewModel) { onIntent(PhotoPrivacyIntent.ScreenStarted) }

    // Toolbar back and system back are the same intent — one rule, not two (§7.5).
    BackHandler { onIntent(PhotoPrivacyIntent.BackPressed) }

    PhotoPrivacyScreen(state = state, onIntent = onIntent, modifier = modifier)
}
