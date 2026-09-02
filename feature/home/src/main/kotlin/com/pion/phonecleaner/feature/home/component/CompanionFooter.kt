package com.pion.phonecleaner.feature.home.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenGutter
import com.pion.phonecleaner.feature.home.R

/**
 * The competitor's `tangughou` footer: how long the app has been installed, and how much has been
 * recovered over that time.
 *
 * Two deliberate differences from the original:
 *
 *  1. **The day count is a `plurals` resource**, not a hand-built `"N day(s)"`. The competitor
 *     appends an English plural by hand in an app that ships 17 locales.
 *  2. **The coloured figure is a second `Text`, not `Html.fromHtml`** over a translated string with
 *     a `<font>` tag in it — a markup tag inside a translatable string is a tag a translator can
 *     break silently (`docs/screens/11-home.md` §1.3).
 *
 * [lifetimeSavedBytes] is a measured total that starts at zero, so `0 B` is the honest reading of it
 * and not a placeholder. The ledger is fed raw bytes at the point the bytes were freed; the
 * competitor accumulates its own by re-parsing the formatted string the previous screen displayed
 * (`md.g4.e()`), which loses about 5 % and defaults an unrecognised unit to MB.
 */
@Composable
internal fun CompanionFooter(
    daysInstalled: Int,
    lifetimeSavedBytes: Long,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.screenGutter().fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FooterFigure(
                label = stringResource(R.string.home_footer_days_label),
                value = pluralStringResource(
                    R.plurals.home_footer_days_value,
                    daysInstalled,
                    daysInstalled,
                ),
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(Modifier.padding(horizontal = Spacing.lg))
            FooterFigure(
                label = stringResource(R.string.home_footer_saved_label),
                value = rememberByteFormat().size(lifetimeSavedBytes).toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FooterFigure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
