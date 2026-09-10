package com.pion.phonecleaner.feature.junk.junkclean

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.junk.R
import com.pion.phonecleaner.feature.junk.component.AnimatedByteCounter
import com.pion.phonecleaner.feature.junk.component.JunkPhaseAnimation
import com.pion.phonecleaner.feature.junk.junkclean.component.CleanFailedPanel
import com.pion.phonecleaner.feature.junk.junkclean.component.StopCleanDialog
import com.pion.phonecleaner.feature.junk.junkclean.component.StorageAccessLostPanel

/**
 * `thenitud.xml` has six views; four survive, and a determinate progress bar is added because here
 * `processed / total` is genuinely known. The competitor has neither a bar nor a ticker on this
 * screen — only a number counting down, and that number is a linear ramp over the promised size
 * (§5.3, Delta C4).
 */
@Composable
internal fun JunkCleanScreen(
    state: JunkCleanState,
    onIntent: (JunkCleanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.junk_clean_title),
                onBack = { onIntent(JunkCleanIntent.BackPressed) },
            )
            when (state.phase) {
                CleanPhase.PermissionLost -> StorageAccessLostPanel(onIntent)
                CleanPhase.Failed -> CleanFailedPanel(state.error, onIntent)
                else -> CleaningPanel(state, onIntent)
            }
        }
        if (state.isStopConfirmVisible) StopCleanDialog(onIntent, recoverable = state.recoverable)
    }
}

@Composable
private fun CleaningPanel(
    state: JunkCleanState,
    onIntent: (JunkCleanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenGutter)
            .padding(top = PageSpacing.headerToContent),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        JunkPhaseAnimation(
            isFinished = state.phase == CleanPhase.Finished,
            onCompletionFinished = { onIntent(JunkCleanIntent.FinishAnimationEnded) },
        )
        // The counter is independent of the deletion loop: a fast clean shows a fast count-down.
        // The competitor awaits a 250 ms tween per path, so forty paths cost ten seconds of
        // animation regardless of filesystem speed (Delta C5).
        AnimatedByteCounter(targetBytes = state.remainingBytes)

        val progress = state.progress
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        Text(
            text = stringResource(
                if (state.phase == CleanPhase.Finished) R.string.junk_clean_finished
                else R.string.junk_clean_cleaning,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Say what was NOT done (§8.3). The competitor's design is arranged so the user is never
        // told less than the maximum defensible figure.
        if (state.hadFailures) {
            Text(
                text = pluralStringResource(
                    R.plurals.junk_clean_failed_count,
                    state.failedCount,
                    state.failedCount,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
