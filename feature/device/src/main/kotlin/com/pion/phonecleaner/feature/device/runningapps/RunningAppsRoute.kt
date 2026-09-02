package com.pion.phonecleaner.feature.device.runningapps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * `runningapps` — callbacks in, nothing out (MVI §4). It never names another feature's route
 * (`LLM.md` §2, §7.1).
 *
 * **The platform work lives here, not in the ViewModel** (§6.2). Two intents are built and started
 * in this file and nowhere else: the app-details page for one package, and the usage-access settings
 * screen. §1.1's `AppInfoLauncher` — an interface in `:domain` returning an
 * `android.content.Intent` — cannot exist, because `:domain` is a `kotlin("jvm")` module with no
 * Android on its classpath (`LLM.md` §2); this is where §6.2 already puts the work.
 *
 * `LifecycleResumeEffect` raises `ScreenResumed` on every `ON_RESUME`. That is the whole
 * verification path: no app is handed a result for a trip to Settings, so the return is the only
 * moment we can re-read `FLAG_STOPPED` (§6.5).
 *
 * The competitor mutes its return-to-app interstitial around this trip. Not ported: an ad host that
 * must be muted before every deep link is mis-configured, and there is no ad in this port.
 */
@Composable
fun RunningAppsRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RunningAppsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is RunningAppsEffect.OpenSystemAppInfo -> openSettings(
                context,
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", effect.packageName, null),
                ),
            )

            // PENDING OWNER DECISION 3 (§0.1) — this opens the system screen; it does not read
            // usage statistics, and PACKAGE_USAGE_STATS is declared in no manifest. Launching this
            // intent needs no declaration.
            RunningAppsEffect.OpenUsageAccessSettings ->
                openSettings(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

            RunningAppsEffect.NavigateBack -> onNavigateBack()

            // The message comes from the effect's own payload, never from `state.error`: the
            // collector runs one main-queue turn after `sendEffect` and a frame before the matching
            // `setState` renders, so reading state here reads the pre-failure value (MVI §4).
            is RunningAppsEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    LifecycleResumeEffect(viewModel) {
        onIntent(RunningAppsIntent.ScreenResumed)
        onPauseOrDispose { }
    }

    BackHandler { onIntent(RunningAppsIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        RunningAppsScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * A device with no activity for the intent is a normal outcome, not a crash: the exception is caught
 * here, in the platform layer that raised it.
 *
 * UNKNOWN — no appendix states what this screen should say when Settings cannot be opened.
 * Looked for: `docs/screens/18-device-battery-and-apps.md` §6.2/§6.5/§7 and the shared
 * `ErrorMessages` table in `:core:ui`. Doing nothing invents no string, and the row is unchanged —
 * which is correct, because nothing was stopped.
 */
private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (notFound: ActivityNotFoundException) {
        @Suppress("UNUSED_EXPRESSION")
        notFound
    }
}
