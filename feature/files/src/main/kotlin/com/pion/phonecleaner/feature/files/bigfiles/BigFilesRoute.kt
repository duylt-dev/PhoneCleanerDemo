package com.pion.phonecleaner.feature.files.bigfiles

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
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
import org.koin.androidx.compose.koinViewModel

/**
 * Callbacks in, nothing out (MVI §4). It never names another feature's route (`LLM.md` §2, §7.1).
 *
 * The two launchers are **platform state living in the composable, reporting upward**: only an
 * Activity can launch a `MediaStore` delete `IntentSender`, which is exactly why
 * `DeleteOutcome.PendingConsent` is an Effect the Route unwraps rather than an error the ViewModel
 * swallows (`LLM.md` §12).
 *
 * `LifecycleStartEffect` re-raises `ScreenStarted` on every `ON_START`, so returning from the SAF
 * tree picker re-enters the same reducer with a wider set of readable roots (`LLM.md` §7.4).
 */
@Composable
fun BigFilesRoute(
    onNavigateToCleanResult: (CleanupSummary) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BigFilesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onIntent = remember(viewModel) { viewModel::onIntent }
    val context = LocalContext.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        onIntent(BigFilesIntent.DeleteConsentResult(result.resultCode == Activity.RESULT_OK))
    }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) {
        // A null tree means the user backed out. `ScreenStarted` re-runs either way, because
        // whichever roots are readable now is what the next scan should cover.
        onIntent(BigFilesIntent.ScreenStarted)
    }

    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is BigFilesEffect.RequestDeleteConsent -> {
                val sender = effect.token.value as? IntentSender
                if (sender == null) {
                    onIntent(BigFilesIntent.DeleteConsentResult(granted = false))
                } else {
                    consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }
            }

            BigFilesEffect.RequestStorageTree -> treeLauncher.launch(null)
            is BigFilesEffect.OpenFile -> openExternally(context, effect.uri, effect.mimeType)
            is BigFilesEffect.NavigateToCleanResult -> onNavigateToCleanResult(effect.summary)
            BigFilesEffect.NavigateBack -> onNavigateBack()
        }
    }

    LifecycleStartEffect(viewModel) {
        onIntent(BigFilesIntent.ScreenStarted)
        onStopOrDispose { }
    }

    BackHandler { onIntent(BigFilesIntent.BackPressed) }

    BigFilesScreen(state = state, onIntent = onIntent, modifier = modifier)
}

/**
 * The MIME type comes from `MimeTypeUseCase`, which reads the `MediaStore` `mime_type` column the row
 * already carries and falls back to the extension. The competitor guesses from the extension only.
 *
 * A device with no viewer for the type is a normal outcome, not a crash: `ActivityNotFoundException`
 * is caught here, in the platform layer that raised it.
 */
internal fun openExternally(
    context: android.content.Context,
    uri: String,
    mimeType: String?,
) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uri), mimeType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(intent)
    } catch (notFound: ActivityNotFoundException) {
        // UNKNOWN — no appendix states what this screen should say when nothing can open a file.
        // Looked for: `docs/screens/14-file-tools-and-app-manager.md` §1.1/§2.4 (which specify the
        // MIME type but not the failure copy) and the shared `ErrorMessages` table in :core:ui.
        // Doing nothing is the conservative branch; it is not a crash and it invents no string.
        @Suppress("UNUSED_EXPRESSION")
        notFound
    }
}
