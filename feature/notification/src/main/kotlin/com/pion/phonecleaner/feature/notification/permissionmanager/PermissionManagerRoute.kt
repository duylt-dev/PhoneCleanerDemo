package com.pion.phonecleaner.feature.notification.permissionmanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.feature.notification.R
import com.pion.phonecleaner.feature.notification.component.openAppSettings
import com.pion.phonecleaner.feature.notification.component.openSpecialAccessSettings
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4).
 *
 * [LifecycleResumeEffect] supplies only the **timing**; the platform reads live in
 * `PermissionRepository` and `RefreshAppPermissionsUseCase`, because they are data-layer questions
 * (`docs/screens/17-notification-and-permissions.md` §4.3). There is no grant callback for any of the
 * six special accesses — a re-read on resume is the only signal that exists — and the competitor's
 * Permission Centre checks once and overrides no `onResume`, so granting and returning leaves the stale
 * row on screen (`LLM.md` §7.4).
 *
 * `jd.t.q()` posts `notifyDataSetChanged()` + `requestLayout()` on **every** resume, a full re-measure
 * of an `ExpandableListView` whether anything changed or not. Nothing here does: a `LazyColumn`
 * re-measures what changed.
 */
@Composable
fun PermissionManagerRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionManagerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PermissionManagerEffect.OpenAppSettings ->
                if (!openAppSettings(context, effect.packageName)) {
                    onIntent(PermissionManagerIntent.ScreenResumed)
                }

            is PermissionManagerEffect.OpenSpecialAccessSettings ->
                if (!openSpecialAccessSettings(context, effect.access, context.packageName)) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.permission_manager_no_settings_screen),
                        )
                    }
                }

            PermissionManagerEffect.NavigateBack -> onNavigateBack()
            // The message comes from the effect's own payload, never from `state.error` (MVI §4).
            is PermissionManagerEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }

            PermissionManagerEffect.ShowScanInProgressMessage -> scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.permission_manager_scan_in_progress),
                )
            }

            PermissionManagerEffect.ShowNoSettingsScreenMessage -> scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.permission_manager_no_settings_screen),
                )
            }
        }
    }

    LifecycleResumeEffect(viewModel) {
        onIntent(PermissionManagerIntent.ScreenResumed)
        onPauseOrDispose { }
    }

    BackHandler { onIntent(PermissionManagerIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        PermissionManagerScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
