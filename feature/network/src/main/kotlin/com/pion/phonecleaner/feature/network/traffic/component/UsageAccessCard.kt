package com.pion.phonecleaner.feature.network.traffic.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.feature.network.R

/**
 * The usage-access wall, folded into this screen (`docs/screens/19-network-and-speed-test.md` §0):
 * work that needs a permission starts from an Intent, so the permission is the screen's own to ask
 * for, and a whole Activity that exists to unblock one screen is that screen's state.
 *
 * The explanation is shown **here, before the user leaves**. The competitor draws a translucent
 * Activity over the system Settings app 500 ms after handing the user to it — a background activity
 * start restricted since Android 10 and largely blocked on 14 (§1.5 D1).
 *
 * PENDING OWNER DECISION 3 — whether *this* screen asks for `PACKAGE_USAGE_STATS`. The manifest now
 * declares it (for App Manager's *Last used* column), so the button reaches a page this app appears
 * on instead of a page it could not. Declared is not granted, so this card is still the screen's real
 * steady state; `ON_START` re-reads the grant either way and nothing here assumes an outcome.
 */
@Composable
internal fun UsageAccessCard(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().padding(horizontal = ScreenGutter)) {
        Column(
            Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.traffic_usage_access_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.traffic_usage_access_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onGrant) {
                Text(stringResource(R.string.traffic_usage_access_action))
            }
        }
    }
}
