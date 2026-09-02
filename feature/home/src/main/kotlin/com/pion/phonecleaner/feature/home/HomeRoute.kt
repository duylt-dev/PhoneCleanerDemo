package com.pion.phonecleaner.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.feature.home.component.HomeDialogHost
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.permission.AppPermission
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out. `:feature:home` does not know that `:feature:junk` exists — the `NavHost`
 * in `:app` wires [onOpenFeature] to a single `when` over the twenty ids (`docs/screens/11-home.md` §3).
 *
 * [onRequestSpecialAccess] is not in the appendix's parameter list and is unavoidable: every
 * `AppPermission` except `Notifications` is a special access opened with an explicit `Settings.ACTION_*`
 * intent, and that mapping is `:data/permission/SpecialAccessIntents.kt` — a module a feature may not
 * see (`LLM.md` §2, §3.6). The launcher below owns the one runtime permission; the host owns the rest.
 */
@Composable
fun HomeRoute(
    onOpenFeature: (FeatureId) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPermissionCentre: () -> Unit,
    onOpenLegal: (LegalDocument) -> Unit,
    onRequestSpecialAccess: (AppPermission) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Held steady once: a 1 Hz rate pill and twenty tiles sit below this lambda (LLM.md §8).
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Resolved from the EFFECT'S OWN payload, never from state: the collector runs one main-queue
    // turn after sendEffect and a frame before the matching setState renders (MVI §4).
    val context = LocalContext.current

    // Platform state lives in the composable and reports upward (MVI §4).
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onIntent(HomeIntent.NotificationPermissionResolved(granted)) }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Below API 33 there is no runtime permission to ask for; the banner stays driven by
            // PermissionRepository.observe() rather than by an answer this screen invents.
            onOpenSettings()
        }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is HomeEffect.NavigateToFeature -> onOpenFeature(effect.feature)
            HomeEffect.NavigateToSettings -> onOpenSettings()
            HomeEffect.NavigateToPermissionCentre -> onOpenPermissionCentre()
            HomeEffect.ExitApp -> onExit()
            HomeEffect.RequestNotificationPermission -> requestNotifications()
            is HomeEffect.RequestPermission ->
                if (effect.permission == AppPermission.Notifications) requestNotifications()
                else onRequestSpecialAccess(effect.permission)
            is HomeEffect.OpenLegalDocument -> onOpenLegal(effect.document)
            // Not awaited: showSnackbar suspends until dismissal, which would queue the next effect.
            is HomeEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(HomeIntent.ScreenStarted)
        onStopOrDispose { onIntent(HomeIntent.ScreenStopped) }
    }
    LifecycleResumeEffect(viewModel) {
        onIntent(HomeIntent.ScreenResumed)
        onPauseOrDispose { }
    }
    BackHandler { onIntent(HomeIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        HomeScreen(state = state, onIntent = onIntent)
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = PageSpacing.snackbarLift),
        )
    }
    state.dialog?.let { HomeDialogHost(dialog = it, onIntent = onIntent) }
}
