package com.pion.phonecleaner.feature.network.traffic.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.core.ui.icon.AppIconLoader
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.network.R
import com.pion.phonecleaner.feature.network.traffic.NetworkTrafficIntent
import com.pion.phonecleaner.feature.network.traffic.TrafficRow
import org.koin.compose.koinInject

/**
 * One app's data use (`docs/screens/19-network-and-speed-test.md` §1.3).
 *
 * The icon is Coil through `AppIconLoader`, `koinInject()`ed **into the composable and never into a
 * ViewModel**. It replaces the competitor's `cd.g`: two `LruCache`s, a hand-rolled 2-thread pool and
 * a view-tag recycling guard.
 *
 * The bar is a length, not a figure: it is `bytes / largest row's bytes`, it is never rendered as a
 * number, and it is excluded from the semantics tree so a screen reader is read the measured size
 * instead of a proportion nobody can act on.
 */
@Composable
internal fun TrafficRowItem(
    row: TrafficRow,
    isStopRequested: Boolean,
    onIntent: (NetworkTrafficIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val icons = koinInject<AppIconLoader>()
    val size = rememberByteFormat().size(row.bytes).toString()
    val packageName = row.packageName
    val onAppInfo = remember(packageName, onIntent) {
        { onIntent(NetworkTrafficIntent.StopPressed(packageName)) }
    }
    val description = stringResource(R.string.traffic_row_description, row.label, size)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = icons.request(packageName),
            imageLoader = icons.imageLoader,
            contentDescription = null, // decorative: the label beside it names the app
            modifier = Modifier.size(RowIconSize),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = Spacing.md)
                .clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = size, style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { row.fractionOfLargest },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = onAppInfo) {
            Text(
                stringResource(
                    if (isStopRequested) {
                        R.string.traffic_app_info_opened
                    } else {
                        R.string.traffic_app_info
                    },
                ),
            )
        }
    }
}

private val RowIconSize = 40.dp
