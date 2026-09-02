package com.pion.phonecleaner.feature.junk.junkreview

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4).
 *
 * [onSessionLost] is the process-death branch `docs/system-architecture.md` §5.2 requires of every
 * session-store route — the case the competitor cannot even detect, because its payload is three
 * `volatile` statics and there is no state in which they are knowably absent.
 */
@Composable
fun JunkReviewRoute(
    onClean: () -> Unit,
    onNavigateBack: () -> Unit,
    onSessionLost: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JunkReviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            JunkReviewEffect.NavigateToClean -> onClean()
            JunkReviewEffect.NavigateBack -> onNavigateBack()
            JunkReviewEffect.NavigateToScan -> onSessionLost()
        }
    }

    BackHandler { onIntent(JunkReviewIntent.BackPressed) }

    JunkReviewScreen(state = state, onIntent = onIntent, modifier = modifier)
}
