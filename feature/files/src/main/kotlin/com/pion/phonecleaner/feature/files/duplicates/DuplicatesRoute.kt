package com.pion.phonecleaner.feature.files.duplicates

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
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1).
 *
 * The consent launcher is **platform state living in the composable, reporting upward**: only an
 * Activity can launch a `MediaStore` delete `IntentSender`, which is why `DeleteOutcome.PendingConsent`
 * is an Effect the Route unwraps rather than an error the ViewModel swallows (`LLM.md` §12).
 *
 * The competitor enters this screen with `FLAG_ACTIVITY_NEW_TASK`, giving it its own task and a back
 * stack the app cannot see. This is a normal nav destination (§2.4).
 */
@Composable
fun DuplicatesRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DuplicatesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        onIntent(DuplicatesIntent.DeleteConsentResult(result.resultCode == Activity.RESULT_OK))
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is DuplicatesEffect.RequestDeleteConsent -> {
                val sender = effect.token.value as? IntentSender
                if (sender == null) {
                    onIntent(DuplicatesIntent.DeleteConsentResult(granted = false))
                } else {
                    consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }

            is DuplicatesEffect.OpenExternally -> openExternally(context, effect.uri, effect.mimeType)
            is DuplicatesEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            DuplicatesEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(DuplicatesIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(DuplicatesIntent.BackPressed) }

    DuplicatesScreen(state = state, onIntent = onIntent, modifier = modifier)
}
