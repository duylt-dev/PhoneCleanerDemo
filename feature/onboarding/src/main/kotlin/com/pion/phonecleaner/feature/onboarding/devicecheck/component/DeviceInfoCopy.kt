package com.pion.phonecleaner.feature.onboarding.devicecheck.component

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pion.phonecleaner.core.ui.format.rememberByteFormat
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoField
import com.pion.phonecleaner.domain.model.onboarding.DeviceInfoValue
import com.pion.phonecleaner.feature.onboarding.R

/**
 * Where a device-check row becomes words.
 *
 * **The row model carries no `@StringRes` and no formatted string** (delta 7): `Striden` stores
 * `titleResId` on the model (`AssimssesActivity.java:104`), which puts an Android resource id in the
 * domain layer and makes the row untestable off device. The mapping lives here, in the only layer
 * that has a `Resources`, and the ViewModel never builds user-facing copy (MVI §5).
 *
 * Byte values are rendered through [rememberByteFormat] — the one formatter, locale-aware, shared
 * with every other screen. `od.p0.i()` and `p0.j()` are two of the competitor's three byte
 * formatters, and they disagree.
 */
@StringRes
internal fun DeviceInfoField.labelRes(): Int = when (this) {
    DeviceInfoField.Device -> R.string.onboarding_device_field_device
    DeviceInfoField.OsVersion -> R.string.onboarding_device_field_os
    DeviceInfoField.ScreenResolution -> R.string.onboarding_device_field_resolution
    DeviceInfoField.ScreenDensity -> R.string.onboarding_device_field_density
    DeviceInfoField.StorageUsed -> R.string.onboarding_device_field_storage
}

/**
 * A null value is a reading that has not happened, or one that failed — and it renders as an em
 * dash, never as `"0KB/0KB"`. That fallback is what every competitor probe returns on failure
 * (`od/p0.java:289-291`), and a user cannot tell it from a device with no storage.
 */
@Composable
internal fun DeviceInfoValue?.render(): String {
    val bytes = rememberByteFormat()
    return when (this) {
        null -> stringResource(R.string.onboarding_device_value_pending)
        is DeviceInfoValue.Text -> text
        is DeviceInfoValue.Pixels ->
            stringResource(R.string.onboarding_device_value_resolution, width, height)

        is DeviceInfoValue.Dpi -> stringResource(R.string.onboarding_device_value_density, densityDpi)
        is DeviceInfoValue.Storage -> stringResource(
            R.string.onboarding_device_value_storage,
            bytes.size(usedBytes).toString(),
            bytes.size(totalBytes).toString(),
        )
    }
}
