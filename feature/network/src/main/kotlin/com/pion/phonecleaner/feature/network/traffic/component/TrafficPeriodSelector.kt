package com.pion.phonecleaner.feature.network.traffic.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.network.TrafficPeriod
import com.pion.phonecleaner.feature.network.traffic.NetworkTrafficIntent

/**
 * The three period tabs. The competitor paints these as a `FrameLayout` with three `TextView`s whose
 * selected state is a hand-applied background and text colour on every tap; a re-tap of the selected
 * tab still re-queries there, and here it is dropped in the reducer.
 *
 * `onIntent` arrives as-is and is not re-wrapped per button (`LLM.md` §8).
 */
@Composable
internal fun TrafficPeriodSelector(
    period: TrafficPeriod,
    onIntent: (NetworkTrafficIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.sm),
    ) {
        TrafficPeriod.entries.forEachIndexed { index, candidate ->
            SegmentedButton(
                selected = candidate == period,
                onClick = { onIntent(NetworkTrafficIntent.SelectPeriod(candidate)) },
                shape = SegmentedButtonDefaults.itemShape(index, TrafficPeriod.entries.size),
            ) {
                Text(stringResource(candidate.labelRes()))
            }
        }
    }
}
