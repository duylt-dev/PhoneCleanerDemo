package com.pion.phonecleaner.core.ui.component.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.pion.phonecleaner.core.ui.token.Spacing

/**
 * One dialog. It replaces `oc.a` and 17 of its 18 subclasses; the three that override the base to
 * move the window to `Gravity.BOTTOM` become [AppBottomSheet], which is a real distinction expressed
 * as a second component rather than as a parameter (system-architecture §4.6).
 *
 * **Visibility lives on the owning screen's `State`** — `state.confirm: ConfirmSpec?`,
 * `state.isConsentVisible`, `state.pendingUnlockPackage: String?`. The competitor builds every dialog
 * as a raw `Dialog` from an Activity with no `DialogFragment` anywhere, so rotating mid-confirm loses
 * the dialog, the pending action, and the only record that it happened (`docs/screens/21` §3.5 D1).
 * What *is* an `Effect` is what a button asks the **system** for: a permission prompt, a Settings
 * screen, an `ACTION_VIEW`, a share sheet.
 *
 * Two competitor dialogs deliberately do **not** come through here: `nc.y`, an `AlertDialog` whose
 * only content is a `ProgressBar`, becomes `state.isLoading` drawn in place with `LoadingOverlay`;
 * `nc.v0`, a full-screen non-cancellable "Preparing ad…" wait, becomes `state.isPreparingAd` with a
 * hard timeout and a visible cancel, marked `[AD GATE: interstitial]` (§4.6).
 *
 * @param dismissLabel `null` gives a single-button dialog. Ten of the eighteen are one.
 * @param onDismiss the dismiss button's action; falls back to [onDismissRequest] when absent.
 * @param dismissible `oc.a`'s `setCancelable`. Five of eighteen were cancellable and thirteen were
 *   not, with no dismiss affordance at all — blocking dismissal inside a permission funnel is a real
 *   product choice and is portable, **but it has to be explicit**, so a screen passing `false` says
 *   why in a comment (§3.5 D4).
 */
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    body: String? = null,
    icon: @Composable (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    dismissible: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        modifier = modifier,
        dismissButton = dismissLabel?.let {
            { TextButton(onClick = onDismiss ?: onDismissRequest) { Text(it) } }
        },
        icon = icon,
        title = title?.let { { Text(it) } },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.md)) {
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
                content()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    )
}
