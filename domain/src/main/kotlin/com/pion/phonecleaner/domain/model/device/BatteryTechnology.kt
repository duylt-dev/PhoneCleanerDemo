package com.pion.phonecleaner.domain.model.device

/**
 * The battery chemistry, as far as it can be recognised
 * (`docs/screens/18-device-battery-and-apps.md` §1, §5.5).
 *
 * [OTHER] is not a failure arm — it is the correct answer whenever the OEM reports a word this
 * mapping does not know, and `BatterySnapshot.rawTechnology` carries that word through verbatim so
 * the cell can render it. The competitor substitutes the literal `"Li-ion"` for a missing *or blank*
 * value, which is a guess about hardware presented as a reading.
 */
enum class BatteryTechnology {
    LI_ION,
    LI_POLY,
    NIMH,
    NICD,
    OTHER,
}
