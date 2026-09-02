package com.pion.phonecleaner.feature.photo.compressrun

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

/**
 * `docs/screens/13-photo-and-media.md` §4.3. Plain navigation lambdas in, nothing out.
 *
 * `popUpTo<PhotoCompressor> { inclusive = true }` on the success hop is `:app`'s to state (§3.5).
 */
@Composable
fun CompressRunRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompressRunViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is CompressRunEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            CompressRunEffect.NavigateBack -> onNavigateBack()
        }
    }

    LaunchedEffect(viewModel) { onIntent(CompressRunIntent.ScreenStarted) }

    BackHandler { onIntent(CompressRunIntent.BackPressed) }

    CompressRunScreen(state = state, onIntent = onIntent, modifier = modifier)
}
