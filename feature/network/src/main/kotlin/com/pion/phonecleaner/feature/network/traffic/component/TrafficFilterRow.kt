package com.pion.phonecleaner.feature.network.traffic.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.dialog.AppBottomSheet
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.network.TrafficFilter
import com.pion.phonecleaner.feature.network.R
import com.pion.phonecleaner.feature.network.traffic.NetworkTrafficIntent

/**
 * The connection filter and the sheet that changes it.
 *
 * The sheet's visibility is a `rememberSaveable` **here**, not an Effect and not a field on
 * `NetworkTrafficState`: `docs/system-architecture.md` §4.6 — *a dialog is a visible condition that
 * must survive a rotation* — and nothing outside this control needs to know it is open. If a future
 * pane switch ever does, it hoists to the state and gains a reducer; it never becomes a
 * `ShowFilterSheet` Effect, which would reopen itself on every rotation.
 */
@Composable
internal fun TrafficFilterRow(
    filter: TrafficFilter,
    onIntent: (NetworkTrafficIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.traffic_connection_label),
            style = MaterialTheme.typography.titleSmall,
        )
        TextButton(onClick = { showSheet = true }) {
            Text(stringResource(filter.labelRes()))
        }
    }

    if (showSheet) {
        AppBottomSheet(onDismissRequest = { showSheet = false }) {
            TrafficFilter.entries.forEach { candidate ->
                TextButton(
                    onClick = {
                        showSheet = false
                        onIntent(NetworkTrafficIntent.SelectFilter(candidate))
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
                ) {
                    Text(
                        text = stringResource(candidate.labelRes()),
                        modifier = Modifier.fillMaxWidth(),
                        style = if (candidate == filter) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                    )
                }
            }
        }
    }
}
