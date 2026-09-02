package com.pion.phonecleaner.domain.model.device

/**
 * Who made the device and which Android it runs
 * (`docs/screens/18-device-battery-and-apps.md` §1).
 *
 * Both are already-joined strings because the join is a `Build.*` concern, not a rendering one:
 * `Build.MODEL` sometimes already begins with the manufacturer and sometimes does not, and that test
 * belongs beside the read, in `:data`.
 */
data class DeviceIdentity(
    val manufacturerAndModel: String,
    val androidRelease: String,
)
