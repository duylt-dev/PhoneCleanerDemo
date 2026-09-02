package com.pion.phonecleaner.core.ui.component.state

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.R
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * Progress drawn **in place**, over the screen it belongs to — not a modal.
 *
 * This is where `nc.y` goes: an `AlertDialog` whose only content is a `ProgressBar`. A modal for a
 * loading state means the screen behind it cannot show progress, cannot be cancelled, and needs a
 * second dismissal path for every failure branch (`docs/screens/21` §3.5 D6). Cancellation here is
 * `viewModelScope`, not `dialog.dismiss()`.
 *
 * It is also the shape `state.isPreparingAd` uses — the competitor's `nc.v0` is a full-screen,
 * non-cancellable 3–10 s wait — which is why [onCancel] exists at all: a modal the user cannot leave
 * is a UX decision to make deliberately, not to inherit (§3.5 D7, `[AD GATE: interstitial]`).
 *
 * The overlay swallows pointer input, so a tap cannot reach a row underneath while work is in flight.
 *
 * UNKNOWN — no source states this component's parameter list. Looked in `LLM.md` §3.5 (which names
 * the file only), `docs/system-architecture.md` §4.7 and `docs/screens/21` §4.1, whose component
 * tables give a contract for the other eleven components and none for this one. The shape below is
 * the conservative reading of the two behaviours §3.5 D6 and D7 do state.
 */
@Composable
fun LoadingOverlay(
    modifier: Modifier = Modifier,
    label: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = ScrimAlpha))
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .padding(horizontal = ScreenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(
            text = label ?: stringResource(R.string.state_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onCancel != null) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

/** A **position** on the opacity scale, not a gap: dim enough to read as blocked, light enough that
 *  the progress behind it — a scan counting up — is still legible. */
private const val ScrimAlpha = 0.55f
