package com.pion.phonecleaner.feature.junk.junkscan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Public and stateful; it takes navigation callbacks in and returns nothing (MVI §4).
 *
 * It never names another feature's route — that is what makes `:feature:A -> :feature:B` unnecessary
 * rather than merely forbidden (`LLM.md` §2, §7.1).
 *
 * The permission launcher is **platform state living in the composable, reporting upward** (MVI §4).
 * `LifecycleStartEffect` re-reports on every `ON_START`, which is the permission funnel of
 * `LLM.md` §7.4: the user grants in Settings, presses back, and the same reducer is re-entered. The
 * competitor's Permission Centre checks once and overrides no `onResume`, so granting and returning
 * leaves the stale card on screen.
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        onIntent(JunkScanIntent.PermissionsResolved(granted.values.any { it }))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            JunkScanEffect.NavigateToReview -> onScanned()
            JunkScanEffect.NavigateBack -> onNavigateBack()
            JunkScanEffect.RequestStoragePermission ->
                permissionLauncher.launch(storageReadPermissions())
            // The message comes from the effect's own payload, never from `state.error`: the
            // collector runs one main-queue turn after `sendEffect` and a frame before the matching
            // `setState` renders, so reading state here reads the pre-failure value (MVI §4).
            is JunkScanEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    LifecycleStartEffect(context, viewModel) {
        onIntent(JunkScanIntent.PermissionsResolved(hasAnyStorageRead(context)))
        onStopOrDispose { }
    }

    BackHandler { onIntent(JunkScanIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        JunkScanScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The read permissions the DEFAULT storage branch asks for.
 *
 * `MANAGE_EXTERNAL_STORAGE` is deliberately not among them: it is never assumed grantable
 * (`docs/system-architecture.md` §8.1), and on API 30+ it does not even open `Android/data` or
 * `Android/obb`, which is where a modern app's leftovers actually are. Android 14's partial media
 * grant is a normal outcome here, not a failure — the scan runs over whatever `StorageRootProvider`
 * then reports as readable, and reports what it covered.
 */
private fun storageReadPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasAnyStorageRead(context: Context): Boolean =
    storageReadPermissions().any { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
