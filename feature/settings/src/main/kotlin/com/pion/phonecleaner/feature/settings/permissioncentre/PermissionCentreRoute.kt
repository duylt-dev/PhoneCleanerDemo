package com.pion.phonecleaner.feature.settings.permissioncentre

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.permission.AppPermission
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (`LLM.md` §7.1). The two launchers are platform state living in the
 * composable and reporting upward (MVI §4).
 *
 * `LifecycleResumeEffect` → `ScreenResumed` on **every** resume is the whole fix for the stale-card
 * bug: the competitor's `c0()` runs only from `z()` and it overrides no `onResume`, so granting in
 * system Settings and pressing back leaves the card on screen
 * (`docs/screens/20-settings-language-and-push.md` §5.4 delta 1).
 */
@Composable
fun PermissionCentreRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionCentreViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    // Which card the in-flight round trip belongs to. A launcher result carries the OS's answer, not
    // the question, so the question is held here for exactly as long as one request is outstanding.
    var pending by remember { mutableStateOf<AppPermission?>(null) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val permission = pending ?: return@rememberLauncherForActivityResult
        pending = null
        // Every one of them, or none: a card that reads "granted" on a partial answer is the
        // competitor's `GONE` card wearing a different hat.
        onIntent(
            PermissionCentreIntent.PermissionResultReceived(
                permission = permission,
                granted = results.isNotEmpty() && results.values.all { it },
            ),
        )
    }

    // A system screen returns no result, so the answer is re-read on resume instead of believed.
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        pending = null
        onIntent(PermissionCentreIntent.ScreenResumed)
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is PermissionCentreEffect.RequestRuntimePermission -> {
                val names = PermissionSystemIntents.runtimePermissions(effect.permission)
                if (names.isEmpty()) {
                    // Nothing to ask on this API level. Reported as a refusal, never as a grant, and
                    // the app-details page is the only remaining route to the setting.
                    onIntent(
                        PermissionCentreIntent.PermissionResultReceived(effect.permission, false),
                    )
                } else {
                    pending = effect.permission
                    runtimeLauncher.launch(names.toTypedArray())
                }
            }

            is PermissionCentreEffect.OpenSystemSettings -> {
                pending = effect.permission
                val intent = PermissionSystemIntents.settingsIntent(context, effect.permission)
                try {
                    settingsLauncher.launch(intent)
                } catch (missing: ActivityNotFoundException) {
                    // Some OEM builds ship no such screen. The app-details page always exists.
                    settingsLauncher.launch(PermissionSystemIntents.appDetails(context))
                }
            }

            PermissionCentreEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleResumeEffect(viewModel) {
        onIntent(PermissionCentreIntent.ScreenResumed)
        onPauseOrDispose { }
    }

    BackHandler { onIntent(PermissionCentreIntent.BackPressed) }

    PermissionCentreScreen(state = state, onIntent = onIntent, modifier = modifier)
}
