package com.pion.phonecleaner.feature.network.traffic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.EmptyState
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.PageSpacing
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.network.R
import com.pion.phonecleaner.feature.network.traffic.component.TrafficFilterRow
import com.pion.phonecleaner.feature.network.traffic.component.TrafficPeriodSelector
import com.pion.phonecleaner.feature.network.traffic.component.TrafficRowItem
import com.pion.phonecleaner.feature.network.traffic.component.TrafficTotalsCard
import com.pion.phonecleaner.feature.network.traffic.component.UsageAccessCard

/**
 * Stateless (`docs/screens/19-network-and-speed-test.md` §1.3). Nothing below the Route sees the
 * ViewModel, and `onIntent` is passed down as-is — a lambda allocated inside `items {}` is a new
 * instance every recomposition and defeats the skip for every row (`LLM.md` §8).
 *
 * A document screen, so the chrome is `PageHeader`; the outermost container owns the insets.
 */
@Composable
internal fun NetworkTrafficScreen(
    state: NetworkTrafficState,
    onIntent: (NetworkTrafficIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.traffic_title),
                onBack = { onIntent(NetworkTrafficIntent.BackPressed) },
            )
            Box(Modifier.weight(1f)) {
                when (state.phase) {
                    NetworkTrafficState.Phase.CheckingAccess,
                    NetworkTrafficState.Phase.Scanning,
                    -> ReadingIndicator()

                    NetworkTrafficState.Phase.NeedsUsageAccess -> UsageAccessCard(
                        onGrant = { onIntent(NetworkTrafficIntent.GrantAccessPressed) },
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.xl),
                    )

                    NetworkTrafficState.Phase.Ready -> TrafficContent(state, onIntent)
                }
            }
            if (state.isReady) {
                Button(
                    onClick = { onIntent(NetworkTrafficIntent.DonePressed) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ScreenGutter, vertical = Spacing.md),
                ) {
                    Text(stringResource(R.string.traffic_done))
                }
            }
        }
    }
}

/** Indeterminate on purpose: the query's duration is not known, so no bar claims to know it. */
@Composable
private fun ReadingIndicator(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = ScreenGutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.traffic_reading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TrafficContent(
    state: NetworkTrafficState,
    onIntent: (NetworkTrafficIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // `rows` is derived from three inputs; held here so it is rebuilt when they change and not on
    // every recomposition of the list.
    val rows = remember(state.report, state.labels, state.filter) { state.rows }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        TrafficPeriodSelector(state.period, onIntent)
        TrafficTotalsCard(state.mobileTotalBytes, state.wifiTotalBytes)
        TrafficFilterRow(state.filter, onIntent)
        state.error?.let { error ->
            ErrorCard(
                error = error,
                onRetry = { onIntent(NetworkTrafficIntent.RetryPressed) },
                modifier = Modifier.padding(horizontal = ScreenGutter),
            )
        }
        if (rows.isEmpty()) {
            EmptyState(message = stringResource(R.string.traffic_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = PageSpacing.listBottom),
            ) {
                items(rows, key = { it.packageName }, contentType = { RowType }) { row ->
                    TrafficRowItem(
                        row = row,
                        isStopRequested = row.packageName == state.stopRequestedFor,
                        onIntent = onIntent,
                    )
                }
            }
        }
    }
}

private const val RowType = "traffic.row"
