package com.pion.phonecleaner.feature.files.audio

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
 * The permission is resolved here — `android.os.Build` and `checkSelfPermission` are platform truth,
 * and platform truth lives in the composable layer — and reported upward as an Intent.
 */
@Composable
fun AudioManagerRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudioManagerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        onIntent(AudioManagerIntent.PermissionResolved(mediaAccessOf(context, MediaKind.Audio)))
    }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        onIntent(AudioManagerIntent.DeleteConsentResult(result.resultCode == Activity.RESULT_OK))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            AudioManagerEffect.RequestMediaPermission ->
                permissionLauncher.launch(mediaPermissions(MediaKind.Audio))

            is AudioManagerEffect.RequestDeleteConsent -> {
                val sender = effect.token.value as? IntentSender
                if (sender == null) {
                    onIntent(AudioManagerIntent.DeleteConsentResult(granted = false))
                } else {
                    consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }

            is AudioManagerEffect.OpenFile -> openExternally(context, effect.uri, effect.mimeType)
            is AudioManagerEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            AudioManagerEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(AudioManagerIntent.ScreenStarted)
        onIntent(AudioManagerIntent.PermissionResolved(mediaAccessOf(context, MediaKind.Audio)))
        onStopOrDispose { }
    }

    BackHandler { onIntent(AudioManagerIntent.BackPressed) }

    AudioManagerScreen(state = state, onIntent = onIntent, modifier = modifier)
}
