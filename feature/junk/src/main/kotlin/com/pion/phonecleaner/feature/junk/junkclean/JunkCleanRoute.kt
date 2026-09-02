package com.pion.phonecleaner.feature.junk.junkclean

import android.Manifest
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.core.ui.error.messageRes
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out.
 *
 * `onCleaned` receives **both** numbers, from the effect's own payload; `:app` builds the
 * `CleanupSummary` and navigates to the one shared `CleanResult` route (§8.1). There is no ad gate
 * between the clean finishing and the result — the competitor puts a full-screen interstitial on the
 * single most rewarding moment of the flow (Delta C11).
 */
@Composable
fun JunkCleanRoute(
    onCleaned: (freedBytes: Long, failedCount: Int) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JunkCleanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // UNKNOWN — §5.1 declares no intent by which this screen can be told the answer, so the
        // result is deliberately not routed anywhere rather than routed into an invented case. See
        // `JunkCleanViewModel.onIntent`.
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is JunkCleanEffect.NavigateToResult -> onCleaned(effect.freedBytes, effect.failedCount)
            JunkCleanEffect.NavigateBack -> onNavigateBack()
            JunkCleanEffect.RequestStoragePermission ->
                permissionLauncher.launch(storageReadPermissions())

            is JunkCleanEffect.ShowMessage -> scope.launch {
                snackbarHostState.showSnackbar(context.getString(effect.error.messageRes()))
            }
        }
    }

    BackHandler { onIntent(JunkCleanIntent.BackPressed) }

    Box(modifier.fillMaxSize()) {
        JunkCleanScreen(state = state, onIntent = onIntent)
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter))
    }
}

/** Same default-branch set as `junkscan`; `MANAGE_EXTERNAL_STORAGE` is never assumed grantable. */
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
