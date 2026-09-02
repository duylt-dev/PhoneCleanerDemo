package com.pion.phonecleaner.feature.device.runningappsscan

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.device.R
import com.pion.phonecleaner.feature.device.component.MemoryRing
import com.pion.phonecleaner.feature.device.component.ScanProgressPanel

/**
 * `runningappsscan` (`docs/screens/18-device-battery-and-apps.md` §6.1). Stateless:
 * `(state, onIntent) -> Unit`, never the ViewModel (MVI §4).
 *
 * The competitor's 270 dp transparent spacer is **deleted**; spacing comes from the token scale. The
 * title is passed to `PageHeader` like every other screen in the cluster — the competitor hard-codes
 * it in this one layout while six sibling screens set it in code (§6.4).
 *
 * `MemoryRing` is the same composable `runningapps` draws, and it reports a measured occupancy with
 * its unit. No string on this screen says anything about what stopping an app would do.
 */
@Composable
internal fun RunningAppsScanScreen(
    state: RunningAppsScanState,
    onIntent: (RunningAppsScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.running_apps_scan_title),
                onBack = { onIntent(RunningAppsScanIntent.BackPressed) },
            )
            Column(
                // Two round widgets stack here — the ring and the counter — and a short screen in
                // landscape does not fit both. It scrolls rather than clipping one of them.
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenGutter)
                    .padding(top = PageSpacing.headerToContent, bottom = Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(RunningAppsScanIntent.BackPressed) },
                    )
                }
                MemoryRing(memory = state.memory)
                ScanProgressPanel(
                    progress = state.progress,
                    caption = stringResource(R.string.running_apps_scan_caption),
                )
            }
        }
    }
}
