package com.pion.phonecleaner.feature.photo.preview

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * `docs/screens/13-photo-and-media.md` §2.3.
 *
 * [onSessionLost] is the process-death branch `LLM.md` §7.2 requires of every session-store route —
 * the case the competitor cannot even detect, because its payload is two static fields.
 */
@Composable
fun PhotoPreviewRoute(
    onNavigateBack: () -> Unit,
    onSessionLost: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoPreviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            PhotoPreviewEffect.NavigateBack -> onNavigateBack()
            PhotoPreviewEffect.NavigateToGrid -> onSessionLost()
        }
    }

    BackHandler { onIntent(PhotoPreviewIntent.ClosePressed) }

    PhotoPreviewScreen(state = state, onIntent = onIntent, modifier = modifier)
}
