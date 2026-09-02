package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.model.device.DeviceMetrics
import com.pion.phonecleaner.domain.model.device.RunningApp
import kotlinx.collections.immutable.ImmutableList

/**
 * The in-memory hand-off from a scan screen to the detail screen it pops itself for
 * (`docs/screens/18-device-battery-and-apps.md` §0.2, `docs/system-architecture.md` §10.3 **U1**;
 * `LLM.md` §7.2's third row).
 *
 * Each scan route pops itself with `popUpTo(self) { inclusive = true }`, and a Koin parameter cannot
 * travel across a `NavHost` edge — which is why the cluster report's
 * `viewModel { (seed: BatterySnapshot?) -> … }` form does not work as written. The three detail
 * routes are therefore **argument-free**, and the payload arrives here.
 *
 * ### The rule that comes with it
 *
 * **Every session-store route owes a "session lost" branch.** If the store is empty on entry —
 * process death, or a deep link — the screen loads for itself instead of rendering a blank frame.
 * That branch is mandatory, not optional; it is the same rule the junk and photo clusters follow.
 *
 * A session is **consumed once**: the reader takes the value and calls [clear]. Never persisted — a
 * scan result is true for the minute it was taken and for no longer.
 */
interface DeviceScanSessionStore {

    var deviceMetrics: DeviceMetrics?

    var batterySnapshot: BatterySnapshot?

    var runningApps: ImmutableList<RunningApp>?

    fun clear()
}
