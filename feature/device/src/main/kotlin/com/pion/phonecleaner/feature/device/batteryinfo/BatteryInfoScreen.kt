package com.pion.phonecleaner.feature.device.batteryinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.device.BatteryCheck
import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.feature.device.R
import com.pion.phonecleaner.feature.device.component.batteryCheckIcon
import com.pion.phonecleaner.feature.device.component.batteryCheckLabel
import com.pion.phonecleaner.feature.device.component.batteryValue

/**
 * `batteryinfo` (`docs/screens/18-device-battery-and-apps.md` §5.3).
 *
 * **`BatteryCheck` is reused verbatim from the scan screen.** The checklist's six steps and this
 * grid's six cells are the same six things in the same order, so a seventh metric is added in one
 * place instead of two.
 *
 * The grid does not scroll — six cells always fit — but it is a `LazyVerticalGrid` because that is
 * what gives each cell its own `key`, so a 1 Hz charging broadcast recomposes the cells that changed
 * and skips the four that did not.
 */
@Composable
internal fun BatteryInfoScreen(
    state: BatteryInfoState,
    onIntent: (BatteryInfoIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            PageHeader(
                title = stringResource(R.string.battery_info_title),
                onBack = { onIntent(BatteryInfoIntent.BackPressed) },
            )
            state.error?.let { error ->
                ErrorCard(
                    error = error,
                    // The feed re-subscribes on its own at the next STARTED; a retry that only
                    // clears the banner would be a button that lies about what it did.
                    onRetry = { onIntent(BatteryInfoIntent.BackPressed) },
                    modifier = Modifier.padding(horizontal = ScreenGutter),
                )
            }
            BatteryInfoHero(
                snapshot = state.snapshot,
                modifier = Modifier.padding(vertical = Spacing.xl),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter),
                userScrollEnabled = false,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                items(
                    items = BatteryCheck.entries,
                    key = { it.name },
                    contentType = { "battery-cell" },
                ) { check ->
                    BatteryMetricCell(check = check, snapshot = state.snapshot)
                }
            }
        }
    }
}

/**
 * One cell: a glyph, a value and a label.
 *
 * It takes the whole [BatterySnapshot] rather than a pre-extracted string so that the cell — not a
 * ViewModel — is what decides how a null reads. The snapshot is `@Immutable` through
 * `compose-stability.conf`'s `domain.model.*` line, so the cell stays skippable.
 */
@Composable
private fun BatteryMetricCell(
    check: BatteryCheck,
    snapshot: BatterySnapshot?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Icon(
            imageVector = batteryCheckIcon(check),
            contentDescription = null, // the label below is the accessible name
            modifier = Modifier.size(CellIconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = batteryValue(check, snapshot),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = batteryCheckLabel(check),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val GRID_COLUMNS = 3

/** A **position**, not a gap (MVI §11): the 24 dp Material list-icon size, as `LabelValueRow` uses. */
private val CellIconSize = 24.dp
