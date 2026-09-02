package com.pion.phonecleaner.feature.files.appmanager

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
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1).
 *
 * **One uninstall round trip at a time.** The launcher reports back with the package it was asked
 * about; the ViewModel re-queries `PackageManager` before counting it. The competitor fires one
 * `ACTION_DELETE` per package in a tight loop — the system stacks N unorderable dialogs — and counts
 * completions from `PACKAGE_REMOVED` broadcasts without inspecting `EXTRA_REPLACING` (§5.5).
 *
 * `LifecycleStartEffect` re-raises `ScreenStarted` on every `ON_START`, which is what makes the
 * return from the usage-access Settings page work: no app is handed a result for that trip.
 */
@Composable
fun AppManagerRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppManagerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    // The package being asked about right now. Read back when the launcher returns, because the
    // result carries no extras of its own.
    val asking = state.uninstalling?.current

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The result code is deliberately ignored: "the dialog closed" is not "the app is gone".
        asking?.let { onIntent(AppManagerIntent.UninstallReturned(it)) }
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            AppManagerEffect.OpenUsageAccessSettings ->
                openSettings(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

            is AppManagerEffect.RequestUninstall -> {
                val intent = Intent(Intent.ACTION_DELETE, packageUri(effect.packageName))
                if (!uninstallLauncher.launchSafely(intent)) {
                    onIntent(AppManagerIntent.UninstallReturned(effect.packageName))
                }
            }

            is AppManagerEffect.OpenAppInfo -> openSettings(
                context,
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    packageUri(effect.packageName),
                ),
            )

            is AppManagerEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            AppManagerEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(AppManagerIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(AppManagerIntent.BackPressed) }

    AppManagerScreen(state = state, onIntent = onIntent, modifier = modifier)
}

private fun packageUri(packageName: String): Uri = Uri.fromParts("package", packageName, null)

/**
 * A device with no activity for the intent is a normal outcome, not a crash: the exception is caught
 * here, in the platform layer that raised it.
 *
 * UNKNOWN — no appendix states what this screen should say when Settings cannot be opened.
 * Looked for: `docs/screens/14-file-tools-and-app-manager.md` §5.3/§5.5 and the shared
 * `ErrorMessages` table in `:core:ui`. Doing nothing invents no string.
 */
private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (notFound: ActivityNotFoundException) {
        @Suppress("UNUSED_EXPRESSION")
        notFound
    }
}

/**
 * Returns `false` when nothing can handle the uninstall intent, so the queue settles that package as
 * declined and moves on instead of stalling on a dialog that never appeared.
 */
private fun androidx.activity.result.ActivityResultLauncher<Intent>.launchSafely(
    intent: Intent,
): Boolean = try {
    launch(intent)
    true
} catch (notFound: ActivityNotFoundException) {
    @Suppress("UNUSED_EXPRESSION")
    notFound
    false
}
