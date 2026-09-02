package com.pion.phonecleaner.feature.antivirus.result

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
 * `Intent.ACTION_DELETE` with a `package:` URI is the system dialog and needs no permission;
 * `ACTION_UNINSTALL_PACKAGE`, which would need `REQUEST_DELETE_PACKAGES`, is deliberately not used.
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
                uninstallLauncher.launch(
                    Intent(Intent.ACTION_DELETE, Uri.fromParts("package", effect.packageName, null)),
                )
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
