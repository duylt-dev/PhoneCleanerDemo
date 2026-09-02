package com.pion.phonecleaner.feature.files.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.mvi.ToolPhase
import com.pion.phonecleaner.core.ui.component.state.LoadingOverlay
import com.pion.phonecleaner.feature.files.R

/**
 * The phase overlay every one of the six tools draws, in one place.
 *
 * `ToolPhase.Completing` is not decoration: **the composable owns the completion animation and
 * reports back with an Intent** ([onCompletionFinished]) — instead of the list being revealed by an
 * ad SDK's close callback, which is a callback that may never arrive
 * (`docs/screens/14-file-tools-and-app-manager.md` §0.3, `LLM.md` §7.1).
 *
 * There is no 4 000 ms floor. `MinimumDuration` exists and this cluster passes it nothing: a screen
 * that finishes in 200 ms finishes in 200 ms (§4).
 */
@Composable
internal fun ToolOverlay(
    phase: ToolPhase,
    onCompletionFinished: () -> Unit,
    modifier: Modifier = Modifier,
    scanningLabel: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    when (phase) {
        ToolPhase.Scanning -> LoadingOverlay(
            modifier = modifier,
            label = scanningLabel,
            onCancel = onCancel,
        )

        ToolPhase.Completing -> {
            LoadingOverlay(modifier = modifier, label = stringResource(R.string.files_finishing))
            // The reveal is driven by the WORK finishing, never by a callback that may not arrive.
            LaunchedEffect(Unit) { onCompletionFinished() }
        }

        ToolPhase.Deleting -> LoadingOverlay(
            modifier = modifier,
            label = stringResource(R.string.files_removing),
        )

        ToolPhase.Idle, ToolPhase.Ready -> Unit
    }
}
