package com.pion.phonecleaner.feature.files.videocompressrun.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.R as CoreUiR
import com.pion.phonecleaner.core.ui.component.dialog.AppDialog
import com.pion.phonecleaner.feature.files.R
import com.pion.phonecleaner.feature.files.videocompressrun.VideoCompressRunIntent
import com.pion.phonecleaner.feature.files.videocompressrun.VideoCompressRunState

/**
 * The three confirms — `plans/260907-0142-video-compression/phase-07-run-screen.md` step 6.
 *
 * **A dialog is state, never an Effect**, so it survives rotation (`LLM.md` §7.4). Each of the three
 * booleans on [VideoCompressRunState] drives exactly one dialog here, each with a confirm AND a
 * dismiss — `docs/screens/13` §4.1 lists a stop confirm with no counterpart, and a confirm that
 * cannot be declined is a trap.
 *
 * The run confirm's body, [R.string.video_compress_run_confirm_body], is the ONLY place the user is
 * told that leaving the screen cancels the job — so it is not optional.
 *
 * Every label on this screen is a `video_compress_*` string of its own. Borrowing another
 * feature's copy — `whatsapp_stop_confirm` reads "Stop" too — would tie this dialog to an
 * unrelated screen's wording, so a WhatsApp copy change would silently reword video runs.
 * `action_cancel` and `action_delete` come from `:core:ui` and are shared verbs by design.
 */
@Composable
internal fun VideoCompressRunDialogs(
    state: VideoCompressRunState,
    onIntent: (VideoCompressRunIntent) -> Unit,
) {
    if (state.isRunConfirmVisible) {
        AppDialog(
            onDismissRequest = { onIntent(VideoCompressRunIntent.CompressDismissed) },
            confirmLabel = stringResource(R.string.video_compress_run_action),
            onConfirm = { onIntent(VideoCompressRunIntent.CompressConfirmed) },
            title = stringResource(R.string.video_compress_run_confirm_title),
            body = stringResource(R.string.video_compress_run_confirm_body),
            dismissLabel = stringResource(CoreUiR.string.action_cancel),
        )
    }
    if (state.isStopConfirmVisible) {
        AppDialog(
            onDismissRequest = { onIntent(VideoCompressRunIntent.StopDismissed) },
            confirmLabel = stringResource(R.string.video_compress_run_stop_action),
            onConfirm = { onIntent(VideoCompressRunIntent.StopConfirmed) },
            title = stringResource(R.string.video_compress_run_stop_title),
            body = stringResource(R.string.video_compress_run_stop_body),
            dismissLabel = stringResource(CoreUiR.string.action_cancel),
        )
    }
    if (state.isDeleteConfirmVisible) {
        AppDialog(
            onDismissRequest = { onIntent(VideoCompressRunIntent.DeleteOriginalsDismissed) },
            confirmLabel = stringResource(CoreUiR.string.action_delete),
            onConfirm = { onIntent(VideoCompressRunIntent.DeleteOriginalsConfirmed) },
            title = stringResource(R.string.video_compress_delete_confirm_title),
            body = stringResource(R.string.video_compress_delete_confirm_body),
            dismissLabel = stringResource(CoreUiR.string.action_cancel),
        )
    }
}
