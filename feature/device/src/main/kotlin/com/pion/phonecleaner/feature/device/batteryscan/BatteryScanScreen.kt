package com.pion.phonecleaner.feature.device.batteryscan

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.device.R
import com.pion.phonecleaner.feature.device.batteryscan.component.BatteryCheckRowItem

/**
 * `batteryscan` (`docs/screens/18-device-battery-and-apps.md` §4.3). Stateless:
 * `(state, onIntent) -> Unit`, never the ViewModel (MVI §4).
 *
 * **A `Column`, not a `LazyColumn`.** A fixed row per `BatteryCheck`, never scrolled;
 * `animateContentSize` replaces
 * the competitor's `DefaultItemAnimator` (add 300 ms, move 200 ms) on a `RecyclerView` that grows one
 * row at a time — which is also why its list cannot survive a config change.
 *
 * The title is **its own**. The competitor calls this screen *and* the detail screen "Battery Info",
 * so its back stack reads as if nothing happened between them (§4.5).
 *
 * `onIntent` is passed down as-is; the rows take only stable parameters, so every row but the one
 * that changed skips on each tick (`LLM.md` §8).
 */
@Composable
internal fun BatteryScanScreen(
    state: BatteryScanState,
    onIntent: (BatteryScanIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.battery_scan_title),
                onBack = { onIntent(BatteryScanIntent.BackPressed) },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter)
                    .padding(top = PageSpacing.headerToContent),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.battery_scan_caption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // The banner sits above the checklist rather than replacing it: the rows already
                // read as unfinished, and hiding them would lose the only thing that says how far
                // the check got before it failed.
                state.error?.let { error ->
                    ErrorCard(
                        error = error,
                        // Back is the honest action: the read has already failed and the screen has
                        // no second source. A "retry" that re-ran the theatre would be 4.5 s of the
                        // same outcome.
                        onRetry = { onIntent(BatteryScanIntent.BackPressed) },
                    )
                }
                BatteryCheckList(state)
            }
        }
    }
}

@Composable
private fun BatteryCheckList(
    state: BatteryScanState,
    modifier: Modifier = Modifier,
) {
    val lastIndex = state.rows.lastIndex
    Column(modifier.fillMaxWidth().animateContentSize()) {
        state.rows.forEachIndexed { index, row ->
            BatteryCheckRowItem(
                row = row,
                isFirst = index == 0,
                isLast = index == lastIndex,
            )
        }
    }
}
