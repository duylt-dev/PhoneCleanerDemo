package com.pion.phonecleaner.feature.files.video

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.pion.phonecleaner.feature.files.bigfiles.openExternally
import com.pion.phonecleaner.feature.files.component.MediaKind
import com.pion.phonecleaner.feature.files.component.mediaAccessOf
import com.pion.phonecleaner.feature.files.component.mediaPermissions
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1).
 *
 * **The permission is resolved HERE and reported upward as an Intent.** `android.os.Build` and
 * `checkSelfPermission` are platform truth, and platform truth lives in the composable layer — a
 * ViewModel that imported either would not run on a bare JVM (MVI §4).
 *
 * `LifecycleStartEffect` re-resolves the grant on every `ON_START`, which is what makes the return
 * from Settings work: no app is handed a result for that trip.
 */
@Composable
fun VideoManagerRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoManagerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        // The map's own booleans are not read: `mediaAccessOf` re-reads the real grant, which is the
        // only thing that distinguishes a full grant from Android 14's user-selected one.
        onIntent(VideoManagerIntent.PermissionResolved(mediaAccessOf(context, MediaKind.Visual)))
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        onIntent(VideoManagerIntent.DeleteConsentResult(result.resultCode == Activity.RESULT_OK))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            VideoManagerEffect.RequestMediaPermission ->
                permissionLauncher.launch(mediaPermissions(MediaKind.Visual))

            is VideoManagerEffect.RequestDeleteConsent -> {
                val sender = effect.token.value as? IntentSender
                if (sender == null) {
                    onIntent(VideoManagerIntent.DeleteConsentResult(granted = false))
                } else {
                    consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }

            is VideoManagerEffect.OpenFile -> openExternally(context, effect.uri, effect.mimeType)
            is VideoManagerEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            VideoManagerEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(VideoManagerIntent.ScreenStarted)
        onIntent(VideoManagerIntent.PermissionResolved(mediaAccessOf(context, MediaKind.Visual)))
        onStopOrDispose { }
    }

    BackHandler { onIntent(VideoManagerIntent.BackPressed) }

    VideoManagerScreen(state = state, onIntent = onIntent, modifier = modifier)
}
