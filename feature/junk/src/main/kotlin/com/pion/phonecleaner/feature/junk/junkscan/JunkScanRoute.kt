package com.pion.phonecleaner.feature.junk.junkscan

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import com.pion.phonecleaner.core.ui.permission.allFilesSettingsIntent
import com.pion.phonecleaner.core.ui.permission.hasFullStorageAccess
import com.pion.phonecleaner.core.ui.permission.legacyStoragePermissions
import com.pion.phonecleaner.core.ui.permission.needsAllFilesSettingsPage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Public and stateful; it takes navigation callbacks in and returns nothing (MVI §4).
 *
 * It never names another feature's route — that is what makes `:feature:A -> :feature:B` unnecessary
 * rather than merely forbidden (`LLM.md` §2, §7.1).
 *
 * The permission launchers are **platform state living in the composable, reporting upward** (MVI §4).
 * `LifecycleStartEffect` re-reports on every `ON_START`, which is the permission funnel of
 * `LLM.md` §7.4: the user grants in Settings, presses back, and the same reducer is re-entered. The
 * competitor's Permission Centre checks once and overrides no `onResume`, so granting and returning
 * leaves the stale card on screen. On API 30+ that re-read is the *only* reliable signal — the
 * all-files Settings page returns `RESULT_CANCELED` whether or not the grant was given.
 *
 * The gate itself is `:core:ui/permission/StorageAccessGate.kt`, shared with the duplicate finder
 * because both ask the identical question: are the SHARED VOLUMES readable? Not whether the media
 * grants are held. That distinction is the whole reason this screen used to report a successful scan
 * of nothing; the file's KDoc has the full account.
 */
@Composable
fun JunkScanRoute(
    onScanned: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JunkScanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Held steady: the subtree below runs an infinite transition and an animated counter, which is
    // the "costs more than a layout pass" case MVI §8 keeps this line for.
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Two launchers, because the platform has two grant shapes and the wrong contract fails silently:
    // `RequestMultiplePermissions` on MANAGE_EXTERNAL_STORAGE returns denied without showing anything.
    // Both report the gate itself back, never the launcher's own result — on API 30+ the Settings page
    // returns RESULT_CANCELED even when the user granted, and below it a partial grant is still a
    // grant we must re-read rather than infer.
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        onIntent(JunkScanIntent.PermissionsResolved(hasFullStorageAccess(context)))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onIntent(JunkScanIntent.PermissionsResolved(hasFullStorageAccess(context)))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            JunkScanEffect.NavigateToReview -> onScanned()
            JunkScanEffect.NavigateBack -> onNavigateBack()
            JunkScanEffect.RequestStoragePermission -> if (needsAllFilesSettingsPage()) {
                allFilesLauncher.launch(allFilesSettingsIntent(context))
            } else {
                permissionLauncher.launch(legacyStoragePermissions())
            }
            // The message comes from the effect's own payload, never from `state.error`: the
            // collector runs one main-queue turn after `sendEffect` and a frame before the matching
            // `setState` renders, so reading state here reads the pre-failure value (MVI §4).
            is JunkScanEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    LifecycleStartEffect(context, viewModel) {
        onIntent(JunkScanIntent.PermissionsResolved(hasFullStorageAccess(context)))
        onStopOrDispose { }
    }

    BackHandler { onIntent(JunkScanIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        JunkScanScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}
