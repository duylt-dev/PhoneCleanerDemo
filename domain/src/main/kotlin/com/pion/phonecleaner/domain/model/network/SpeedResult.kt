package com.pion.phonecleaner.domain.model.network

/**
 * A finished measurement: bytes per second in each direction, as measured.
 *
 * Bytes per second, not a rating: `ByteFormatter.rate()` turns either number into a value and a unit
 * at render time, and nothing anywhere calls a connection fast, slow, good or poor (`LLM.md` §1).
 *
 * This type exists only for a measurement that actually moved bytes. There is no "estimated" or
 * "assumed" constructor, because the competitor's whole speed test is one: it transfers nothing and
 * divides a device-wide `TrafficStats` delta by a wall clock that includes an advert's dwell time
 * (`docs/reverse-engineering/19-network-and-speed-test.md`, chapter §4.2).
 */
data class SpeedResult(
    val downloadBytesPerSecond: Long,
    val uploadBytesPerSecond: Long,
)
