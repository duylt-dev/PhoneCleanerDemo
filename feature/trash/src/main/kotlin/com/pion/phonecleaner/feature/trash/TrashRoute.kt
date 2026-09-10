package com.pion.phonecleaner.feature.trash

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1).
 *
 * `ShowMessage`/`ShowRestored` render through a snackbar, the shape `HiddenNotificationsRoute` uses
 * (the phase-05 spec names `BigFilesRoute` for this, but `BigFilesEffect` declares no `ShowMessage` at
 * all to copy — noted in the phase implementation report).
 *
 * [LifecycleResumeEffect] re-raises `ScreenResumed` on every `ON_RESUME`, including the trip back from
 * the all-files Settings page `onRequestAllFilesAccess` opens (`LLM.md` §7.4). `ScreenStarted` fires
 * once, from the first composition, and is idempotent past the first call (`TrashViewModel.onScreenStarted`).
 */
@Composable
fun TrashRoute(
    onNavigateBack: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrashViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            TrashEffect.NavigateBack -> onNavigateBack()
            TrashEffect.RequestAllFilesAccess -> onRequestAllFilesAccess()

            // From the effect's own payload, never `state.error` — the collector runs one main-queue
            // turn before the matching `setState` renders (MVI §4).
            is TrashEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(resources.getString(effect.error.messageRes()))
            }

            is TrashEffect.ShowRestored -> scope.launch {
                snackbarHostState.showSnackbar(context.trashRestoredMessage(effect))
            }
            is TrashEffect.ShowDeleteFailures -> scope.launch {
                snackbarHostState.showSnackbar(
                    context.resources.getQuantityString(R.plurals.trash_delete_failed_count, effect.failed, effect.failed),
                )
            }
        }
    }

    LifecycleResumeEffect(viewModel) {
        onIntent(TrashIntent.ScreenResumed)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) { onIntent(TrashIntent.ScreenStarted) }

    BackHandler { onIntent(TrashIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        TrashScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** Never overwrites (`TrashRepository.restore`'s own KDoc), so a rename or a leftover is disclosed here. */
private fun Context.trashRestoredMessage(effect: TrashEffect.ShowRestored): String {
    val parts = buildList {
        add(resources.getQuantityString(R.plurals.trash_restored_count, effect.restored, effect.restored))
        if (effect.renamed > 0) {
            add(resources.getQuantityString(R.plurals.trash_restored_renamed, effect.renamed, effect.renamed))
        }
        if (effect.failed > 0) {
            add(resources.getQuantityString(R.plurals.trash_failed_count, effect.failed, effect.failed))
        }
    }
    return parts.joinToString(separator = " ")
}
