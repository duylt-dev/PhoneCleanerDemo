package com.pion.phonecleaner.feature.photo.compressrun.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.R as CoreUiR
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.feature.photo.R
import com.pion.phonecleaner.feature.photo.compressrun.CompressRunIntent
import com.pion.phonecleaner.feature.photo.compressrun.CompressRunState

/**
 * Both confirms — `docs/screens/13-photo-and-media.md` §4.1, §4.2.
 *
 * **A dialog is state, never an Effect**, so it survives rotation (`LLM.md` §8). §4.1 spells the
 * field `confirm: ConfirmSpec?`; two booleans are used instead, as in `privacy`, because a
 * `ConfirmSpec` is a `:core:ui` type and a ViewModel may not import one (MVI §4).
 *
 * The count is a `<plurals>` with a quantity, never a hand-appended "s".
 */
@Composable
internal fun CompressRunDialogs(
    state: CompressRunState,
    onIntent: (CompressRunIntent) -> Unit,
) {
    if (state.isCompressConfirmVisible) {
        AppDialog(
            onDismissRequest = { onIntent(CompressRunIntent.CompressDismissed) },
            confirmLabel = stringResource(R.string.photo_compress_run_action),
            onConfirm = { onIntent(CompressRunIntent.CompressConfirmed) },
            title = stringResource(R.string.photo_compress_confirm_title),
            body = pluralStringResource(
                R.plurals.photo_compress_confirm_body,
                state.photos.size,
                state.photos.size,
            ),
            dismissLabel = stringResource(CoreUiR.string.action_cancel),
        )
    }
    if (state.isStopConfirmVisible) {
        AppDialog(
            onDismissRequest = { onIntent(CompressRunIntent.CancelRunDismissed) },
            confirmLabel = stringResource(R.string.photo_action_stop),
            onConfirm = { onIntent(CompressRunIntent.CancelRunConfirmed) },
            title = stringResource(R.string.photo_compress_stop_title),
            body = stringResource(R.string.photo_compress_stop_body),
            dismissLabel = stringResource(CoreUiR.string.action_cancel),
        )
    }
}
