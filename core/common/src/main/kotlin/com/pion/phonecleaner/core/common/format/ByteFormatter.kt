package com.pion.phonecleaner.core.common.format

import java.util.Locale
import kotlin.math.abs

/**
 * A byte count split into number and unit, so the UI can style them separately —
 * the competitor's hero figures render the two at different sizes.
 *
 * Immutable, and declared stable in `compose-stability.conf` (LLM.md §8).
 */
data class FormattedSize(val value: String, val unit: String) {
    override fun toString(): String = "$value $unit"
}

/**
 * Stateless. Deliberately NOT in Koin — a Koin binding for an object with no dependencies and no
 * swappable implementation is ceremony (LLM.md §6.3).
 */
object ByteFormatter {

    private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")
    private val RATE_UNITS = arrayOf("B/s", "KB/s", "MB/s", "GB/s")

    fun size(bytes: Long, locale: Locale = Locale.getDefault()): FormattedSize =
        format(bytes, SIZE_UNITS, locale)

    fun rate(bytesPerSecond: Long, locale: Locale = Locale.getDefault()): FormattedSize =
        format(bytesPerSecond, RATE_UNITS, locale)

    private fun format(bytes: Long, units: Array<String>, locale: Locale): FormattedSize {
        var magnitude = abs(bytes).toDouble()
        var index = 0
        while (magnitude >= 1024.0 && index < units.lastIndex) {
            magnitude /= 1024.0
            index++
        }
        if (bytes < 0) magnitude = -magnitude
        val pattern = if (index == 0 || magnitude >= 100.0) "%.0f" else "%.1f"
        return FormattedSize(String.format(locale, pattern, magnitude), units[index])
    }
}
