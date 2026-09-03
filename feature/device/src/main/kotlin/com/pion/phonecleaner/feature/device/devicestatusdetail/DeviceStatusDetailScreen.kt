package com.pion.phonecleaner.feature.device.devicestatusdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.component.header.PageHeader
import com.pion.phonecleaner.core.ui.component.state.ErrorCard
import com.pion.phonecleaner.core.ui.component.tile.LabelValueRow
import com.pion.phonecleaner.core.ui.token.ScreenGutter
import com.pion.phonecleaner.core.ui.token.Spacing
import com.pion.phonecleaner.core.ui.token.screenInsetsPadding
import com.pion.phonecleaner.feature.device.R
import com.pion.phonecleaner.feature.device.component.MetricCard

/**
 * `devicestatusdetail` (`docs/screens/18-device-battery-and-apps.md` §3.3).
 *
 * The competitor's `NestedScrollView` over a fixed column of six blocks becomes a **`LazyColumn`** —
 * not because the list is long, but because each card is an independently recomposing item keyed by
 * its own nullable, so a 1 Hz battery tick does not re-run the processor card.
 *
 * `onIntent` is passed down as-is and each card's action lambda is `remember`ed on it, so no new
 * instance is allocated per recomposition (`LLM.md` §8).
 */
@Composable
internal fun DeviceStatusDetailScreen(
    state: DeviceStatusDetailState,
    onIntent: (DeviceStatusDetailIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onCheckMemory = remember(onIntent) {
        { onIntent(DeviceStatusDetailIntent.CheckMemoryTapped) }
    }
    val onCheckStorage = remember(onIntent) {
        { onIntent(DeviceStatusDetailIntent.CheckStorageTapped) }
    }
    val onCheckBattery = remember(onIntent) {
        { onIntent(DeviceStatusDetailIntent.CheckBatteryTapped) }
    }

    Surface(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().screenInsetsPadding()) {
            PageHeader(
                title = stringResource(R.string.device_status_detail_title),
                onBack = { onIntent(DeviceStatusDetailIntent.BackPressed) },
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = ScreenGutter,
                    end = ScreenGutter,
                    top = Spacing.md,
                    bottom = Spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item(key = "error", contentType = "error") {
                    state.error?.let { error ->
                        ErrorCard(
                            error = error,
                            onRetry = { onIntent(DeviceStatusDetailIntent.RetryTapped) },
                        )
                    }
                }
                item(key = "hero", contentType = "hero") {
                    DeviceHeroBanner(state)
                }
                item(key = "memory", contentType = "card") {
                    MetricCard(
                        icon = Icons.Filled.Memory,
                        title = stringResource(R.string.device_status_card_memory),
                        percent = state.memory?.usedPercent,
                        onAction = onCheckMemory,
                    ) {
                        ColumnVolumeRows(state.memory?.totalBytes, state.memory?.availableBytes)
                    }
                }
                item(key = "storage", contentType = "card") {
                    MetricCard(
                        icon = Icons.Filled.Storage,
                        title = stringResource(R.string.device_status_card_storage),
                        percent = state.storage?.let { storage ->
                            if (storage.totalBytes > 0) {
                                ((storage.usedBytes * 100) / storage.totalBytes).toInt()
                            } else {
                                null
                            }
                        },
                        onAction = onCheckStorage,
                    ) {
                        ColumnVolumeRows(state.storage?.totalBytes, state.storage?.availableBytes)
                    }
                }
                item(key = "battery", contentType = "card") {
                    MetricCard(
                        icon = Icons.Filled.BatteryFull,
                        title = stringResource(R.string.device_status_card_battery),
                        percent = state.battery?.percent,
                        onAction = onCheckBattery,
                    ) {
                        ColumnBatteryRows(state.battery)
                    }
                }
                // No Check button: there is nothing to navigate to, so the slot is not filled and
                // the button does not exist in the composition.
                item(key = "cpu", contentType = "card") {
                    MetricCard(
                        icon = Icons.Filled.DeveloperBoard,
                        title = stringResource(R.string.device_status_card_cpu),
                        percent = state.cpu?.busyPercent,
                        onAction = null,
                    ) {
                        ColumnCpuRows(state.cpu)
                    }
                }
                item(key = "display", contentType = "card") {
                    MetricCard(
                        icon = Icons.Filled.Smartphone,
                        title = stringResource(R.string.device_status_card_display),
                        percent = null,
                        onAction = null,
                    ) {
                        ColumnDisplayRows(state.display)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceHeroBanner(state: DeviceStatusDetailState) {
    val unread = stringResource(R.string.device_status_unread)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = state.identity?.manufacturerAndModel?.ifBlank { unread } ?: unread,
            style = MaterialTheme.typography.headlineSmall,
        )
        LabelValueRow(
            icon = null,
            label = stringResource(R.string.device_status_row_android),
            value = state.identity?.androidRelease?.ifBlank { unread } ?: unread,
        )
    }
}
