package com.pion.phonecleaner.data.device

import android.content.Context
import android.content.res.Resources
import android.provider.Settings

/**
 * The screen brightness as a percentage of the panel's own range — or `null`.
 *
 * `docs/screens/18-device-battery-and-apps.md` §5.5 records two separate defects in the competitor's
 * one line, `Settings.System.getInt(cr, "screen_brightness", 128) × 100 / 255`:
 *
 * 1. It assumes a 0–255 range. OEMs ship 0–1023 and 0–4095 panels, on which the figure is a quarter
 *    or a sixteenth of the truth.
 * 2. In **automatic** mode the stored value is not what is on the panel at all, so the percentage is
 *    meaningless — and the competitor renders it anyway, falling back to a literal `50` on any
 *    exception.
 *
 * Both become `null` here, which the cell renders as "Auto" or "Not available" rather than a number.
 *
 * The maximum is read from the platform's own `config_screenBrightnessSettingMaximum` integer by
 * name. There is no public API for it; `getIdentifier` against the `android` package is the only
 * non-reflective path, and it returning 0 on a build that hides the resource is a normal outcome —
 * the answer is then `null`, not an assumed 255.
 */
internal object ScreenBrightnessReader {

    /** True when the panel is driven by the light sensor, so the stored value means nothing. */
    fun isAutomatic(context: Context): Boolean = runCatching {
        Settings.System.getInt(
            context.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
        ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    }.getOrDefault(false)

    fun percent(context: Context): Int? {
        if (isAutomatic(context)) return null
        val maximum = maximumSetting() ?: return null
        val current = runCatching {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrNull() ?: return null
        if (maximum <= 0 || current < 0) return null
        return ((current * 100) / maximum).coerceIn(0, 100)
    }

    private fun maximumSetting(): Int? = runCatching {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier(MAXIMUM_SETTING_RESOURCE, "integer", "android")
        if (id == 0) null else resources.getInteger(id).takeIf { it > 0 }
    }.getOrNull()

    private const val MAXIMUM_SETTING_RESOURCE = "config_screenBrightnessSettingMaximum"
}
