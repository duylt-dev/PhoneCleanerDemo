package com.pion.phonecleaner.feature.files.whatsapp

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
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pion.phonecleaner.core.mvi.CollectEffects
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1).
 *
 * The legacy `/WhatsApp` root is an **opt-in SAF tree**, launched here because only a composable can
 * launch one. `MANAGE_EXTERNAL_STORAGE` is never requested (§0.2). Whether that tree makes the
 * legacy root reachable on a given API level is recorded as design intent, confidence medium, not
 * verified per API level (§6.5).
 *
 * `LifecycleStartEffect` re-raises `ScreenStarted` on every `ON_START`, so returning from the tree
 * picker re-enters the same reducer with whatever roots are readable now.
 */
@Composable
fun WhatsAppCleanerRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WhatsAppCleanerViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        onIntent(
            WhatsAppCleanerIntent.DeleteConsentResult(result.resultCode == Activity.RESULT_OK),
        )
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) {
        // A null tree means the user backed out. `ScreenStarted` re-runs either way, because
        // whichever roots are readable now is what the next scan should cover.
        onIntent(WhatsAppCleanerIntent.ScreenStarted)
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            WhatsAppCleanerEffect.RequestStorageTree -> treeLauncher.launch(null)

            is WhatsAppCleanerEffect.RequestDeleteConsent -> {
                val sender = effect.token.value as? IntentSender
                if (sender == null) {
                    onIntent(WhatsAppCleanerIntent.DeleteConsentResult(granted = false))
                } else {
                    consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }

            is WhatsAppCleanerEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            WhatsAppCleanerEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(WhatsAppCleanerIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(WhatsAppCleanerIntent.BackPressed) }

    WhatsAppCleanerScreen(state = state, onIntent = onIntent, modifier = modifier)
}
