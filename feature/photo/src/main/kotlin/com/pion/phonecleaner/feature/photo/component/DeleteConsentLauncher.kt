package com.pion.phonecleaner.feature.photo.component

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pion.phonecleaner.domain.model.file.PendingIntentToken

/**
 * Turns a [PendingIntentToken] into the system consent dialog, and the dialog's answer into a
 * plain `Boolean` the reducer can take.
 *
 * **Platform state lives in the composable and reports upward** (MVI §4). Only an Activity can
 * launch an `IntentSender`, which is exactly why `DeleteOutcome.PendingConsent` travels as far as
 * the Route and no further down (`docs/system-architecture.md` §8.4).
 *
 * The token's payload is `PendingIntent.getIntentSender()`, put there by
 * `:data/storage/DefaultFileDeleter`. A token carrying anything else is reported as a declined
 * consent rather than crashing the screen: `PendingIntentToken(val value: Any)` is untyped by
 * design, so this is the one place the shape has to be checked.
 */
@Composable
fun rememberDeleteConsentLauncher(onResult: (Boolean) -> Unit): (PendingIntentToken) -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> onResult(result.resultCode == Activity.RESULT_OK) }
    return remember(launcher, onResult) {
        { token ->
            val sender = token.value as? IntentSender
            if (sender == null) onResult(false) else {
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
        }
    }
}
