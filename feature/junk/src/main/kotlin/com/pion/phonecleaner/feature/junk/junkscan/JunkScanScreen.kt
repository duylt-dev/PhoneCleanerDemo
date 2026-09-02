package com.pion.phonecleaner.feature.junk.junkscan

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
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.junk.R
import com.pion.phonecleaner.feature.junk.component.AnimatedByteCounter
import com.pion.phonecleaner.feature.junk.component.JunkPhaseAnimation
import com.pion.phonecleaner.feature.junk.junkscan.component.ScanFailedPanel
import com.pion.phonecleaner.feature.junk.junkscan.component.ScanPathTicker
import com.pion.phonecleaner.feature.junk.junkscan.component.StopScanDialog
import com.pion.phonecleaner.feature.junk.junkscan.component.StoragePermissionPanel

/**
 * Stateless: `(state, onIntent) -> Unit`, never the ViewModel (MVI §4).
 *
 * `alienaisa.xml` is a `ConstraintLayout` of eight views; six survive, and the ad container is not
 * one of them (`docs/screens/12-junk-cleaning.md` §3.3). `wc.e`'s 513-line progress subsystem is
 * gone with it.
 *
 * It renders `PageHeader` and never writes its own header (MVI §11). The outermost container takes
 * the inset, because in landscape the cutout moves to one side and the whole page moves with it.
 */
@Composable
internal fun JunkScanScreen(
    state: JunkScanState,
    onIntent: (JunkScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.junk_title),
                onBack = { onIntent(JunkScanIntent.BackPressed) },
            )
            when (state.phase) {
                JunkScanPhase.PermissionRequired -> StoragePermissionPanel(onIntent)
                JunkScanPhase.Failed -> ScanFailedPanel(state.error, onIntent)
                else -> ScanningPanel(state, onIntent)
            }
        }
        if (state.isStopConfirmVisible) StopScanDialog(onIntent)
    }
}

@Composable
private fun ScanningPanel(
    state: JunkScanState,
    onIntent: (JunkScanIntent) -> Unit,
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
            isFinished = state.phase == JunkScanPhase.Finished,
            onCompletionFinished = { onIntent(JunkScanIntent.CompletionAnimationFinished) },
        )
        AnimatedByteCounter(targetBytes = state.foundBytes)

        // Null-safe by design: the indeterminate variant is used for the one pass that genuinely
        // cannot report a total, rather than a target the data cannot justify (Delta S1).
        val progress = state.progress
        if (progress == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }

        Text(
            text = stringResource(
                if (state.phase == JunkScanPhase.Finished) R.string.junk_scan_finished
                else R.string.junk_scan_scanning,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ScanPathTicker(state.currentPath)
    }
}
