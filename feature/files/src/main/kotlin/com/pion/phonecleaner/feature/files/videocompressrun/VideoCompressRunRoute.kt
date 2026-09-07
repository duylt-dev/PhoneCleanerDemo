package com.pion.phonecleaner.feature.files.videocompressrun

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). Never names another feature's route (`LLM.md` §2, §7.1).
 *
 * The delete-consent launcher is `VideoManagerRoute`'s ten lines, copied whole (key insight 5):
 * `rememberDeleteConsentLauncher` lives in `:feature:photo` and a `:feature` module may not see
 * another one's code (`LLM.md` §2). This screen needs no `LifecycleStartEffect` — it asks for no
 * permission of its own; the media grant was resolved by the picker and the delete grant comes
 * through the consent dialog.
 */
@Composable
fun VideoCompressRunRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoCompressRunViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        onIntent(VideoCompressRunIntent.DeleteConsentResult(result.resultCode == Activity.RESULT_OK))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is VideoCompressRunEffect.RequestDeleteConsent -> {
                // A wrong payload is a declined consent, never a crash — the one place this
                // untyped token is checked (`LLM.md` §12).
                val sender = effect.token.value as? IntentSender
                if (sender == null) {
                    onIntent(VideoCompressRunIntent.DeleteConsentResult(granted = false))
                } else {
                    consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }

            is VideoCompressRunEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            VideoCompressRunEffect.NavigateBack -> onNavigateBack()
        }
    }

    LaunchedEffect(viewModel) { onIntent(VideoCompressRunIntent.ScreenStarted) }

    BackHandler { onIntent(VideoCompressRunIntent.BackPressed) }

    VideoCompressRunScreen(state = state, onIntent = onIntent, modifier = modifier)
}
