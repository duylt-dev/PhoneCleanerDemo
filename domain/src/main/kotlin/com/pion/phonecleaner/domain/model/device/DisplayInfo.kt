package com.pion.phonecleaner.domain.model.device

/**
 * The panel, in the only units the platform actually reports
 * (`docs/screens/18-device-battery-and-apps.md` §1, §3.5).
 *
 * There is no "screen quality" figure. The competitor draws a progress track at `densityDpi / 640`,
 * which is not a measurement of anything; the bar is dropped and the two numbers are shown plain.
 */
data class DisplayInfo(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
)
