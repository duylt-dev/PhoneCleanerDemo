package com.pion.phonecleaner.feature.photo.blurry

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.feature.photo.component.rememberDeleteConsentLauncher
import org.koin.androidx.compose.koinViewModel

/**
 * Plain navigation lambdas in, nothing out.
 *
 * The consent launcher is the reason `DeleteOutcome.PendingConsent` travels as far as the Route and
 * no further down: only an Activity can launch an `IntentSender`, and its answer comes straight back
 * as an ordinary Intent (`docs/system-architecture.md` §8.4).
 */
@Composable
fun BlurryPhotosRoute(
    onOpenPreview: (String, Int) -> Unit,
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BlurryPhotosViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val requestConsent = rememberDeleteConsentLauncher { granted ->
        onIntent(BlurryPhotosIntent.DeleteConsentResult(granted))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is BlurryPhotosEffect.OpenPreview -> onOpenPreview(effect.groupKey, effect.startIndex)
            is BlurryPhotosEffect.RequestDeleteConsent -> requestConsent(effect.token)
            is BlurryPhotosEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            BlurryPhotosEffect.NavigateBack -> onNavigateBack()
        }
    }

    LaunchedEffect(viewModel) { onIntent(BlurryPhotosIntent.ScreenStarted) }

    BackHandler { onIntent(BlurryPhotosIntent.BackPressed) }

    BlurryPhotosScreen(state = state, onIntent = onIntent, modifier = modifier)
}
