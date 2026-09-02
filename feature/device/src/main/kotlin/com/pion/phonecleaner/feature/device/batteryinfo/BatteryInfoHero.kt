package com.pion.phonecleaner.feature.device.batteryinfo

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.model.device.ChargeState
import com.pion.phonecleaner.feature.device.R
import kotlin.time.Duration

/**
 * The hero block of `batteryinfo` (`docs/screens/18-device-battery-and-apps.md` §5.3).
 *
 * `Crossfade` replaces the competitor's `visibility` flipping between a static icon and a charging
 * animation, and it needs no "is it already animating?" guard, because the composition is the answer.
 *
 * The status label, the icon bucket, the *remaining* wording and the `h`/`min` split are all
 * **derived here**. The competitor writes each of them into its binding as a pre-formatted string,
 * which is how the same reading ends up phrased two ways on two screens.
 */
@Composable
internal fun BatteryInfoHero(
    snapshot: BatterySnapshot?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Crossfade(targetState = snapshot?.chargeState, label = "battery-hero") { state ->
            Icon(
                imageVector = heroIcon(state, snapshot?.percent),
                contentDescription = null, // the percentage below is the accessible name
                modifier = Modifier.size(HeroIconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = (snapshot?.percent ?: 0).toString(),
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = stringResource(R.string.device_percent_symbol),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = stringResource(statusLabelRes(snapshot?.chargeState)),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(remainingLabelRes(snapshot?.chargeState)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = remainingText(snapshot?.chargeTimeRemaining),
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

/**
 * The competitor's own four buckets — `≥ 80 / ≥ 50 / ≥ 20 / else` — kept, because they are a
 * reasonable reading of a battery glyph and nothing about them is a claim.
 */
private fun heroIcon(state: ChargeState?, percent: Int?): ImageVector = when {
    state == ChargeState.CHARGING || state == ChargeState.FULL -> Icons.Filled.BatteryChargingFull
    percent == null -> Icons.Filled.Battery3Bar
    percent >= 80 -> Icons.Filled.BatteryFull
    percent >= 50 -> Icons.Filled.Battery5Bar
    percent >= 20 -> Icons.Filled.Battery3Bar
    else -> Icons.Filled.Battery1Bar
}

private fun statusLabelRes(state: ChargeState?): Int = when (state) {
    ChargeState.CHARGING -> R.string.battery_state_charging
    ChargeState.FULL -> R.string.battery_state_full
    // Null is the first frame; "On battery" is the state a phone is in unless told otherwise, and it
    // makes no claim either way.
    ChargeState.DISCHARGING, null -> R.string.battery_state_discharging
}

private fun remainingLabelRes(state: ChargeState?): Int = when (state) {
    ChargeState.CHARGING -> R.string.battery_remaining_charging
    else -> R.string.battery_remaining_discharging
}

/**
 * `null` renders "Not available" — it does **not** render an estimate.
 *
 * The competitor answers `percent × 480 / 100` minutes while discharging: a flat
 * eight-hours-at-full assumption whose only input is the percentage, so the "estimate" is the
 * percentage in another unit. `AndroidBatteryRepository` supplies a `Duration` only when
 * `BatteryManager` computed one.
 */
@Composable
private fun remainingText(remaining: Duration?): String {
    if (remaining == null) return stringResource(R.string.value_unavailable)
    val totalMinutes = remaining.inWholeMinutes
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) {
        stringResource(R.string.battery_duration_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.battery_duration_minutes, minutes)
    }
}

private const val MINUTES_PER_HOUR = 60L

/** A **position**, not a gap (MVI §11): 96 dp is the size at which the glyph reads as the page's hero. */
private val HeroIconSize = 96.dp
