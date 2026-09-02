package com.pion.phonecleaner.feature.photo.compressor

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import kotlinx.collections.immutable.ImmutableList
import org.koin.androidx.compose.koinViewModel

/**
 * `docs/screens/13-photo-and-media.md` §3.3. Plain navigation lambdas in, nothing out.
 *
 * `popUpTo<PhotoCompressor> { inclusive = true }` on the run screen's success navigation is a
 * **nav-graph fact** `:app` states; the competitor closes this picker from the run screen through a
 * static callback (§3.5).
 */
@Composable
fun PhotoCompressorRoute(
    onOpenCompressRun: (ImmutableList<Long>) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoCompressorViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PhotoCompressorEffect.OpenCompressRun -> onOpenCompressRun(effect.ids)
            PhotoCompressorEffect.NavigateBack -> onNavigateBack()
        }
    }

    LaunchedEffect(viewModel) { onIntent(PhotoCompressorIntent.ScreenStarted) }

    // Toolbar back and system back are one rule, not two.
    BackHandler { onIntent(PhotoCompressorIntent.BackPressed) }

    PhotoCompressorScreen(state = state, onIntent = onIntent, modifier = modifier)
}
