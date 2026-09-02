package com.pion.phonecleaner.feature.device.runningapps.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppBottomSheet
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.device.R

/**
 * The force-stop instructions (`docs/screens/18-device-battery-and-apps.md` §7) — the competitor's
 * seventh Activity, **folded, not ported** (`LLM.md` §3.10).
 *
 * It has no route, no entry in the nav graph and no `Contract.kt`. Its only state is
 * `RunningAppsState.instructionsFor`, which already exists and already survives the trip out and
 * back.
 *
 * Four things it is not:
 *
 * 1. **Not a background activity start.** The competitor launches a translucent Activity from the
 *    application context with `NEW_TASK` *while the user is in Settings* — a background launch,
 *    restricted from Android 10 and largely blocked on 14, so its appearance cannot be relied on;
 *    and when it does appear it is our window over the OS's. This sheet is shown while we are still
 *    the foreground app, so nothing can block it.
 * 2. **Not a fake control.** The competitor draws a "Force stop" button that looks like the system
 *    one, sits over the system screen, and does nothing but dismiss itself — a user who taps it
 *    believes the app was stopped. Here there is one primary action and it says where it goes.
 * 3. **Not on a timer.** The competitor's 500 ms `postDelayed` races the Settings launch in both
 *    directions. The order of operations is the whole delta: sheet → "Open settings" →
 *    `OpenSystemAppInfo` → `startActivity`.
 * 4. **Not dismissed by a tap anywhere.** A scrim tap dismisses without navigating, and the sheet
 *    re-opens by tapping Stop again, so an accidental touch does not lose the instruction the user
 *    was about to follow.
 *
 * ### DEVIATION — where this file lives
 *
 * §7 places it in `:core:ui/component/`, because the competitor's *second* caller is
 * `LawsustincActivity` in the network cluster and one shared sheet should serve both. `:core:ui` is
 * not this cluster's to write, and no network screen exists yet to share it with, so it is here and
 * the promotion is reported to the owner of `:core:ui`. Nothing about the composable changes when it
 * moves: it is already stateless and takes three parameters.
 */
@Composable
internal fun ForceStopInstructionsSheet(
    packageName: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.running_apps_instructions_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.running_apps_instructions_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // The sheet says plainly that this app cannot stop another app itself, and that it will
            // only mark one as stopped if Android reports it that way. That sentence is the whole
            // reason the sheet exists (§6.5).
            Text(
                text = stringResource(R.string.running_apps_instructions_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.running_apps_instructions_open))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.running_apps_instructions_cancel))
            }
        }
    }
}
