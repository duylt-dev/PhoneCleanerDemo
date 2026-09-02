package com.pion.phonecleaner.domain.model.device

/**
 * RAM totals, in bytes (`docs/screens/18-device-battery-and-apps.md` §1).
 *
 * `usedPercent` is an **occupancy figure** — how full the volume is right now. It is not, and must
 * never be rendered as, a claim about speed, headroom or what stopping an app would gain: the
 * wording ban (`LLM.md` §1) covers exactly this cell, because it is where the competitor's device and
 * battery screens make their performance claims. A ring may draw it; no string may interpret it.
 */
data class MemoryInfo(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    val usedPercent: Int get() =
        if (totalBytes > 0) ((usedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}
