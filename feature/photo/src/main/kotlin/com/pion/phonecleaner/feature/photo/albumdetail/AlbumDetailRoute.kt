package com.pion.phonecleaner.feature.photo.albumdetail

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
 * `docs/screens/13-photo-and-media.md` §7.2. Callbacks in, nothing out.
 *
 * `popUpTo<Albums>` after a successful delete is a **nav-graph fact** that `:app` states; this Route
 * only says "the clean finished, here is the summary". The competitor finishes its parent with
 * `zd.c.e("ExeestiaActivity")` — a class-simple-name lookup in a global Activity registry (§7.5).
 */
@Composable
fun AlbumDetailRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val requestConsent = rememberDeleteConsentLauncher { granted ->
        onIntent(AlbumDetailIntent.DeleteConsentResult(granted))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is AlbumDetailEffect.RequestDeleteConsent -> requestConsent(effect.token)
            is AlbumDetailEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            AlbumDetailEffect.NavigateBack -> onNavigateBack()
        }
    }

    LaunchedEffect(viewModel) { onIntent(AlbumDetailIntent.ScreenStarted) }

    BackHandler { onIntent(AlbumDetailIntent.BackPressed) }

    AlbumDetailScreen(state = state, onIntent = onIntent, modifier = modifier)
}
