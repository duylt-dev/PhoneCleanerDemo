package com.pion.phonecleaner.feature.antivirus.result

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Public and stateful; navigation callbacks in, nothing out (MVI §4). It never names another
 * feature's route (`LLM.md` §2, §7.1).
 *
 * **The system uninstall goes through `rememberLauncherForActivityResult`, not `startActivity`.**
 * The competitor fires it and never learns the outcome, so a cancelled uninstall leaves its row
 * greyed for ever (§2.5). The md5 of the row in flight is remembered here — visual local state that
 * belongs to this launcher and to nothing else — and reported back as `UninstallReturned`.
 *
 * `Intent.ACTION_DELETE` with a `package:` URI is the system dialog, and it **does** need
 * `REQUEST_DELETE_PACKAGES` — declared in this module's manifest, which states the measurement.
 * Without it the uninstaller finishes inside `onCreate`: no exception and no dialog, so the tap
 * looks broken and the launcher still comes back as if the user had refused. `ACTION_UNINSTALL_PACKAGE`
 * is still deliberately not used; it is deprecated and needs the same permission.
 */
@Composable
fun AntivirusResultRoute(
    onNavigateToScan: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AntivirusResultViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Which row the system dialog was opened for. `mutableStateOf` rather than a ViewModel field:
    // it is the launcher's own bookkeeping, and it dies with the launcher.
    val inFlightMd5 = remember { mutableStateOf<String?>(null) }

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        // The result code of ACTION_DELETE is not a reliable statement about what happened, so it is
        // not read: the row's flag is lowered, and the PACKAGE_REMOVED broadcast — matched against
        // the list in the reducer — is what actually removes a row.
        inFlightMd5.value?.let { md5 -> onIntent(AntivirusResultIntent.UninstallReturned(md5)) }
        inFlightMd5.value = null
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is AntivirusResultEffect.LaunchUninstall -> {
                inFlightMd5.value = effect.md5
                if (!uninstallLauncher.launchSafely(uninstallIntent(effect.packageName))) {
                    // Nothing on this device can uninstall. Lower the row's flag here rather than
                    // leaving it greyed for ever waiting on a dialog that never appeared — the same
                    // outcome the launcher would report, reached without a crash.
                    onIntent(AntivirusResultIntent.UninstallReturned(effect.md5))
                    inFlightMd5.value = null
                }
            }

            AntivirusResultEffect.NavigateToScan -> onNavigateToScan()
            AntivirusResultEffect.NavigateBack -> onNavigateBack()

            // The message comes from the effect's own payload, never from `state.error`: the
            // collector runs one main-queue turn after `sendEffect` and a frame before the matching
            // `setState` renders (MVI §4).
            is AntivirusResultEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    PackageRemovalEffect(onIntent)

    LifecycleStartEffect(viewModel) {
        onIntent(AntivirusResultIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(AntivirusResultIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        AntivirusResultScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The system uninstall request for one flagged package.
 *
 * `REQUEST_DELETE_PACKAGES` is declared in this module's manifest and is what makes this intent do
 * anything at all: the system uninstaller refuses an `ACTION_DELETE` from a `targetSdk >= 28` caller
 * that does not hold it, and refuses it by finishing inside `onCreate` — no exception and no dialog.
 * That manifest states the measurement.
 *
 * `EXTRA_RETURN_RESULT` keeps the outcome in the launcher instead of the uninstaller's own
 * "Uninstalled" toast, which would land on top of this screen's snackbar.
 */
private fun uninstallIntent(packageName: String): Intent =
    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
        .putExtra(Intent.EXTRA_RETURN_RESULT, true)

/**
 * A device with no activity for the uninstall intent is a normal outcome, not a crash: the exception
 * is caught here, in the platform layer that raised it. Returns whether the dialog was actually
 * asked for, which is what lets the caller settle the row instead of stranding it.
 *
 * The same guard `AppManagerRoute` uses. It is duplicated rather than shared because `:feature:files`
 * and `:feature:antivirus` may not depend on each other (`LLM.md` §2), and four lines of platform
 * defence is not a reason to add a helper to `:core:ui`.
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
