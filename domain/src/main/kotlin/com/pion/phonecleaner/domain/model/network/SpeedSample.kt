package com.pion.phonecleaner.domain.model.network

/**
 * One reading taken **while** a measurement runs.
 *
 * PROVISIONAL, and knowingly so: `docs/screens/19-network-and-speed-test.md` §5 open item 3 records
 * that this shape "is defined by whatever measurement P2 settles on". It is written now because the
 * screen needs a type to render, and it is small enough that settling P2 rewrites one implementation
 * rather than a screen.
 *
 * [progressPercent] is a position in the measurement, `0..100` — how far through the transfer it is.
 * It is not a figure about the device and is never rendered as a claim.
 */
data class SpeedSample(
    val stage: SpeedTestStage,
    val progressPercent: Int,
    val instantBytesPerSecond: Long,
)
