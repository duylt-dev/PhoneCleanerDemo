package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.tile.LabelValueRow
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenGutter
import com.pion.phonecleaner.feature.home.HomeIntent
import com.pion.phonecleaner.feature.home.JunkPill
import com.pion.phonecleaner.feature.home.R
import com.pion.phonecleaner.feature.home.StorageUsage
import com.pion.phonecleaner.core.ui.R as CoreUiR

/**
 * The hero card: the storage ring, the two byte figures, the junk line and the one action.
 *
 * **Bytes are formatted here, not in the ViewModel** (delta 14). The competitor puts two different
 * formatters on this one card — `od.p0.i`, a `DecimalFormat` forced to `Locale.US`, and `wc.j`, a
 * `String.format` on the default locale that never emits `B` — so the same card punctuates two
 * figures two ways. `rememberByteFormat()` reads the *render* locale, so the in-app language picker
 * changes these figures without a process restart.
 *
 * The ring's percentage is storage occupancy. It is the one percentage this app shows, and it is a
 * measurement of how full the volume is, never a claim about the device
 * (`docs/screens/11-home.md` §4.2).
 *
 * The ring and the two figures are measurements of the volume and stay live even when the clean
 * itself is `FeatureAvailability.comingSoon`: what is deferred is the action, not the reading.
 */
@Composable
internal fun CleanHeroCard(
    storage: StorageUsage,
    junkPill: JunkPill,
    needsSecurityScanToday: Boolean,
    isBusy: Boolean,
    isComingSoon: Boolean,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val format = rememberByteFormat()
    Card(modifier = modifier.screenGutter().fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StorageRing(usedPercent = storage.usedPercent)
                Column(Modifier.weight(1f).padding(start = Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.home_storage_title),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        // The competitor's `equialt`, which is a bare gone/visible red dot with no
                        // accessible name and no explanation anywhere on the screen.
                        if (needsSecurityScanToday) {
                            AttentionDot(stringResource(R.string.home_safety_check_due))
                        }
                    }
                    LabelValueRow(
                        icon = null,
                        label = stringResource(R.string.home_storage_used),
                        value = format.size(storage.usedBytes).toString(),
                    )
                    LabelValueRow(
                        icon = null,
                        label = stringResource(R.string.home_storage_free),
                        value = format.size(storage.freeBytes).toString(),
                    )
                }
            }
            Text(
                text = junkLine(junkPill, isBusy, isComingSoon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(
                    onClick = { onIntent(HomeIntent.CleanTapped) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy && !isComingSoon,
                ) {
                    Text(
                        stringResource(
                            if (isComingSoon) R.string.home_tile_coming_soon
                            else CoreUiR.string.feature_cta_clean,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Three named states, resolved once here from a modelled type — four, once the clean itself may be
 * unavailable.
 *
 * The competitor infers the same three from two booleans and a `-1` sentinel *at render time*
 * (`N1()` :750-770), which is why "we have not looked yet" and "there is nothing here" render
 * identically on its card (delta 13).
 *
 * [isComingSoon] is read **before** the pill and not beside it: a cached figure plus "not ready yet"
 * are two statements about the same card that contradict each other, and the honest one is the one
 * about what the button will do.
 */
@Composable
private fun junkLine(pill: JunkPill, isBusy: Boolean, isComingSoon: Boolean): String {
    if (isComingSoon) return stringResource(R.string.home_junk_coming_soon)
    if (isBusy) return stringResource(R.string.home_junk_checking)
    val format = rememberByteFormat()
    return when (pill) {
        JunkPill.NotMeasured -> stringResource(R.string.home_junk_not_measured)
        JunkPill.Clean -> stringResource(R.string.home_junk_none)
        is JunkPill.Measured ->
            stringResource(R.string.home_junk_measured, format.size(pill.bytes).toString())
    }
}
