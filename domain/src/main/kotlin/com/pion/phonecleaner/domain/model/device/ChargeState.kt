package com.pion.phonecleaner.domain.model.device

/**
 * Whether the battery is filling, draining or full
 * (`docs/screens/18-device-battery-and-apps.md` §1, §5.5).
 *
 * **There is deliberately no `CHARGING_FAST`.** The competitor derives "fast" from
 * `plugged == AC || plugged == WIRELESS`, which calls wireless charging fast and USB-PD not fast —
 * backwards on most modern devices. A claim the platform does not supply is not made at all.
 */
enum class ChargeState {
    DISCHARGING,
    CHARGING,
    FULL,
}
