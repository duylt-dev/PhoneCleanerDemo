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
            AppManagerEffect.OpenUsageAccessSettings -> openUsageAccessSettings(context)

            is AppManagerEffect.RequestUninstall -> {
                if (!uninstallLauncher.launchSafely(uninstallIntent(effect.packageName))) {
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
 * The system uninstall request for ONE package.
 *
 * Two things have to be true for this intent to show anything, and only one of them lives here.
 * `:feature:files`' manifest declares `REQUEST_DELETE_PACKAGES` — the system uninstaller refuses an
 * `ACTION_DELETE` from a `targetSdk >= 28` caller that does not hold it, and it refuses it by
 * finishing inside `onCreate`: no exception, no dialog, no result extras, just a tap that did
 * nothing while this screen counted the package as declined. That manifest states the measurement.
 *
 * `EXTRA_RETURN_RESULT` is the second. It asks the uninstaller to hand the outcome back through the
 * launcher rather than announce it itself; without it the system posts its own "Uninstalled" toast
 * per package, so a five-app queue stacks five toasts over our progress bar and then over the
 * clean-result screen. The result code it returns is still not read — see the launcher above, the
 * re-query is the truth — but the toast is suppressed either way.
 */
private fun uninstallIntent(packageName: String): Intent =
    Intent(Intent.ACTION_DELETE, packageUri(packageName))
        .putExtra(Intent.EXTRA_RETURN_RESULT, true)

/**
 * This app's own row on the system Usage Access page, with the plain list as the fallback.
 *
 * Two things have to be true for the card's button to lead anywhere, and only one of them lives
 * here. `:data`'s manifest declares `PACKAGE_USAGE_STATS` — that page lists ONLY apps that declare
 * it, so without the declaration this launch opened a page the app could never appear on. The
 * `package:` URI is the second: it opens this app's own toggle instead of a list to scroll.
 *
 * The fallback is not defensive noise. A device whose Settings has no per-app usage-access activity
 * matches nothing — the list filter declares no `data` — so the launch throws and the list, which
 * every device has, is opened instead. Resolved to
 * `com.android.settings.Settings$AppUsageAccessSettingsActivity` on SM-A165F / Android 16.
 */
private fun openUsageAccessSettings(context: Context) {
    val list = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    val own = Intent(list).setData(packageUri(context.packageName))
    if (!openSettings(context, own)) openSettings(context, list)
}

/**
 * A device with no activity for the intent is a normal outcome, not a crash: the exception is caught
 * here, in the platform layer that raised it. Returns whether the screen actually opened, which is
 * what lets [openUsageAccessSettings] fall back instead of leaving the user on a tap that did
 * nothing.
 *
 * UNKNOWN — no appendix states what this screen should say when Settings cannot be opened.
 * Looked for: `docs/screens/14-file-tools-and-app-manager.md` §5.3/§5.5 and the shared
 * `ErrorMessages` table in `:core:ui`. Doing nothing invents no string.
 */
private fun openSettings(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (notFound: ActivityNotFoundException) {
    @Suppress("UNUSED_EXPRESSION")
    notFound
    false
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
