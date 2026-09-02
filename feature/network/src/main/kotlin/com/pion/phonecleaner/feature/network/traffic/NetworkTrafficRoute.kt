package com.pion.phonecleaner.feature.network.traffic

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1).
 *
 * `LifecycleStartEffect` re-raises `ScreenStarted` on every `ON_START`, which is what makes the
 * return from either system page work: no app is handed a result for a trip to Settings, so the
 * screen re-reads the grant and re-runs the query rather than trusting a result code.
 *
 * The launcher is still used for the usage-access page, because starting it through
 * `rememberLauncherForActivityResult` keeps the trip owned by this composable rather than by a bare
 * `Context` reference; its callback simply re-raises `ScreenStarted`.
 */
@Composable
fun NetworkTrafficRoute(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NetworkTrafficViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // System screens return no result of their own. Re-read instead of believing one.
        onIntent(NetworkTrafficIntent.ScreenStarted)
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            NetworkTrafficEffect.OpenUsageAccessSettings ->
                settingsLauncher.launchSafely(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

            is NetworkTrafficEffect.OpenAppDetails -> openSettings(
                context,
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", effect.packageName, null),
                ),
            )

            NetworkTrafficEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(NetworkTrafficIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(NetworkTrafficIntent.BackPressed) }

    NetworkTrafficScreen(state = state, onIntent = onIntent, modifier = modifier)
}

/**
 * A device with no activity for the intent is a normal outcome, not a crash: the exception is caught
 * here, in the platform layer that raised it.
 *
 * UNKNOWN — no appendix states what this screen should say when Settings cannot be opened. Looked
 * for: `docs/screens/19-network-and-speed-test.md` §1.3/§1.5 and the shared `ErrorMessages` table in
 * `:core:ui`. Doing nothing invents no string.
 */
private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (notFound: ActivityNotFoundException) {
        @Suppress("UNUSED_EXPRESSION")
        notFound
    }
}

private fun androidx.activity.result.ActivityResultLauncher<Intent>.launchSafely(intent: Intent) {
    try {
        launch(intent)
    } catch (notFound: ActivityNotFoundException) {
        @Suppress("UNUSED_EXPRESSION")
        notFound
    }
}
