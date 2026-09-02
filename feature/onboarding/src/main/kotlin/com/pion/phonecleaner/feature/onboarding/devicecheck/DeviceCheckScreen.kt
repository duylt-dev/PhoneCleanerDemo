package com.pion.phonecleaner.feature.onboarding.devicecheck

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.error.userMessage
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.onboarding.R
import com.pion.phonecleaner.feature.onboarding.devicecheck.component.DeviceInfoRowItem

/**
 * Stateless: `(state, onIntent) -> Unit`, never the ViewModel (MVI §4).
 *
 * `sketcrat.xml` is a `ConstraintLayout` of seven views; five survive. The `sustio` ad slot is not
 * one of them — the ad boundary has no owner yet, and a placeholder that reserves 198 dp for
 * something that may never ship is the competitor's invisible-consent-row defect wearing a different
 * hat. The header Lottie is not one either: `assets/lt/scan_device/data.json` has no equivalent in
 * this project (see [DeviceCheckRoute] for what was looked for).
 *
 * It renders `PageHeader` and never writes its own (MVI §11). The header takes no `onBack`: back
 * during the sequence is swallowed by the Route's `BackHandler`, and once the CTA is live the CTA is
 * the exit. A back arrow that sometimes does nothing is worse than no back arrow.
 */
@Composable
internal fun DeviceCheckScreen(
    state: DeviceCheckState,
    onIntent: (DeviceCheckIntent) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(title = stringResource(R.string.onboarding_device_check_title))
            Text(
                text = stringResource(R.string.onboarding_device_check_note),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenGutter)
                    .padding(top = PageSpacing.headerToContent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { error ->
                Text(
                    text = error.userMessage(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenGutter)
                        .padding(top = Spacing.sm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            DeviceInfoRows(state, listState, Modifier.weight(1f))
            ContinueButton(state, onIntent)
        }
    }
}

/**
 * `key = { it.field }` + a `contentType`, so identity is a domain value and never a position: the
 * competitor carries the position in the row's `View` tag and casts it back (`ud/c.java:89,136`).
 *
 * `onIntent` is not passed into `items { }` at all — nothing in a row is clickable, so there is no
 * per-row lambda to allocate (`LLM.md` §8).
 */
@Composable
private fun DeviceInfoRows(
    state: DeviceCheckState,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(
            start = ScreenGutter,
            end = ScreenGutter,
            top = PageSpacing.headerToContent,
            bottom = Spacing.lg,
        ),
    ) {
        itemsIndexed(
            items = state.rows,
            key = { _, row -> row.field },
            contentType = { _, _ -> "device-info-row" },
        ) { index, row ->
            DeviceInfoRowItem(
                row = row,
                isFirst = index == 0,
                isLast = index == state.rows.lastIndex,
                // `DefaultItemAnimator` add 300 / move 200 (`AssimssesActivity:857-860`), kept.
                modifier = Modifier.animateItem(
                    fadeInSpec = tween(RowFadeInMillis),
                    placementSpec = tween(RowPlacementMillis),
                ),
            )
        }
    }
}

/**
 * `enhanuccee`, as a real `Button`: `enabled` is announced to accessibility services, which a
 * `TextView.isEnabled` swapping two background drawables is not.
 *
 * The label is built here, from state — the ViewModel never builds user-facing copy (MVI §5).
 */
@Composable
private fun ContinueButton(
    state: DeviceCheckState,
    onIntent: (DeviceCheckIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (state.isCountingDown) {
        stringResource(R.string.onboarding_device_check_cta_countdown, state.countdownSeconds)
    } else {
        stringResource(R.string.onboarding_device_check_cta)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter)
            .padding(bottom = PageSpacing.headerToContent),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Button(
            onClick = { onIntent(DeviceCheckIntent.ContinueClicked) },
            modifier = Modifier.fillMaxWidth(),
            enabled = state.isContinueEnabled && !state.isBusy,
        ) {
            Text(label)
        }
    }
}

private const val RowFadeInMillis = 300
private const val RowPlacementMillis = 200
