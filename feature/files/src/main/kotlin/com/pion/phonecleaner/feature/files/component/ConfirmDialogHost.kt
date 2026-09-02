package com.pion.phonecleaner.feature.files.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.core.ui.component.dialog.ConfirmSpec

/**
 * **A dialog is a nullable field on `State`, never an Effect** (`LLM.md` §7.4). All 18 competitor
 * dialogs vanish on rotation, structurally; this one is state, so `onCleared` has nothing to dismiss
 * and a rotation redraws it.
 *
 * One host for all six tools, because six identical `state.confirm?.let { AppDialog(...) }` blocks is
 * six places for the Cancel label to drift.
 */
@Composable
internal fun ConfirmDialogHost(
    spec: ConfirmSpec?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (spec == null) return
    AppDialog(
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(spec.confirmRes),
        onConfirm = onConfirm,
        title = stringResource(spec.titleRes),
        // A plural body, never a hand-appended "s": the competitor writes
        // "Delete <n> videos (12.3MB )" with a literal trailing space and no plural rule at all.
        body = pluralStringResource(spec.bodyRes, spec.count, spec.count),
        dismissLabel = stringResource(com.pion.phonecleaner.core.ui.R.string.action_cancel),
        onDismiss = onDismiss,
    )
}
