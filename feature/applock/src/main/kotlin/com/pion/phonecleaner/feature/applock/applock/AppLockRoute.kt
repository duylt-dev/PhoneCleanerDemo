package com.pion.phonecleaner.feature.applock.applock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.repository.PermissionRepository
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Callbacks in, nothing out (`LLM.md` §7.1).
 *
 * [onRequestSpecialAccess] is the same parameter `HomeRoute` takes and for the same reason: both of
 * App Lock's permissions are **special accesses** opened with an explicit `Settings.ACTION_*`
 * intent, and that mapping lives in `:data/permission`, a module a feature may not see
 * (`LLM.md` §2, §3.6). The ViewModel never builds an `Intent`; this composable does not either.
 */
@Composable
fun AppLockRoute(
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    onRequestSpecialAccess: (AppPermission) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppLockViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Held steady once: a list of every launcher app sits below this lambda (`LLM.md` §8).
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            AppLockEffect.NavigateToSettings -> onNavigateToSettings()
            AppLockEffect.NavigateBack -> onNavigateBack()
            AppLockEffect.RequestOverlayPermission -> onRequestSpecialAccess(AppPermission.Overlay)
            AppLockEffect.RequestUsageStatsPermission ->
                onRequestSpecialAccess(AppPermission.UsageStats)
            // Resolved from the EFFECT'S OWN payload, never from state: the collector runs one
            // main-queue turn after sendEffect and a frame before the matching setState renders.
            is AppLockEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    PermissionReporter(onIntent)
    BackHandler { onIntent(AppLockIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        AppLockScreen(state = state, onIntent = onIntent)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PageSpacing.snackbarLift),
        )
    }
}

/**
 * Re-reads both special accesses on every `ON_START`, replacing the competitor's `onResume() → N()`.
 *
 * **There is no grant callback for either of them**, so re-reading on start is the only signal that
 * exists — and it is exactly what the competitor's Permission Centre lacks, which leaves a stale
 * card on screen after the user grants and presses back (`LLM.md` §7.4).
 *
 * `PermissionRepository` is `koinInject()`ed **in the composable**: platform state lives here and
 * reports upward through an Intent (MVI §4). A ViewModel holding it would be a ViewModel that
 * imports the platform.
 */
@Composable
private fun PermissionReporter(onIntent: (AppLockIntent) -> Unit) {
    val permissions = koinInject<PermissionRepository>()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                onIntent(
                    AppLockIntent.PermissionsResolved(
                        overlay = permissions.isGranted(AppPermission.Overlay),
                        usageStats = permissions.isGranted(AppPermission.UsageStats),
                    ),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
