package com.pion.phonecleaner.feature.photo.albums

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
 * Callbacks in, nothing out (MVI §4). The Route names no other feature's route type — `:app` owns
 * the graph (`LLM.md` §7.1).
 */
@Composable
fun AlbumsRoute(
    onOpenAlbum: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is AlbumsEffect.OpenAlbum -> onOpenAlbum(effect.folderName)
            AlbumsEffect.NavigateBack -> onNavigateBack()
        }
    }

    // Re-entered after a delete in the album screen; the reducer is idempotent (§6.2).
    LifecycleResumeEffect(viewModel) {
        onIntent(AlbumsIntent.ScreenStarted)
        onPauseOrDispose { }
    }

    BackHandler { onIntent(AlbumsIntent.BackPressed) }

    AlbumsScreen(state = state, onIntent = onIntent, modifier = modifier)
}
