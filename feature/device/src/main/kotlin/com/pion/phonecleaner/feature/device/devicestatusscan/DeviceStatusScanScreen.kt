package com.pion.phonecleaner.feature.device.devicestatusscan

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
import com.pion.phonecleaner.feature.device.component.ScanProgressPanel

/**
 * `devicestatusscan` (`docs/screens/18-device-battery-and-apps.md` §2.3). Stateless:
 * `(state, onIntent) -> Unit`, never the ViewModel (MVI §4).
 *
 * The caption is a **device-status** caption. The competitor's reads *"Checking background apps…"* on
 * its device-status scan — its own copy defect, shipped on the wrong screen (§2.5).
 *
 * It renders `PageHeader` and never writes its own header (MVI §11). The outermost container takes
 * the inset, because in landscape the cutout moves to one side and the whole page moves with it.
 */
@Composable
internal fun DeviceStatusScanScreen(
    state: DeviceStatusScanState,
    onIntent: (DeviceStatusScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.device_status_scan_title),
                onBack = { onIntent(DeviceStatusScanIntent.BackPressed) },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter)
                    .padding(top = PageSpacing.headerToContent),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xl, Alignment.CenterVertically),
            ) {
                // Back is the honest action: a failed read has no second source on this screen, and
                // re-running the beat would be three more seconds of the same outcome.
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        onRetry = { onIntent(DeviceStatusScanIntent.BackPressed) },
                    )
                }
                ScanProgressPanel(
                    progress = state.progress,
                    caption = stringResource(R.string.device_status_scan_caption),
                )
            }
        }
    }
}
