package com.pion.phonecleaner.data.device

import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import com.pion.phonecleaner.domain.model.device.DeviceMetrics
import com.pion.phonecleaner.domain.model.device.RunningApp
import com.pion.phonecleaner.domain.repository.DeviceScanSessionStore
import kotlinx.collections.immutable.ImmutableList

/**
 * The scan-to-detail hand-off (`docs/screens/18-device-battery-and-apps.md` §0.2,
 * `docs/system-architecture.md` §10.3 **U1**). A `single` in `deviceDataModule`, and **never
 * persisted** — a scan result is true for the minute it was taken.
 *
 * `@Volatile` on each field rather than a lock: a scan ViewModel writes on its own coroutine's thread
 * and the next screen's ViewModel reads on the main thread, so the only requirement is that the write
 * is visible. There is no read-modify-write anywhere, so there is nothing to serialise.
 *
 * This replaces a `startActivity` that carried nothing at all: the competitor's running-apps scan
 * enumerates every package, **discards the list**, and lets the next screen enumerate again.
 */
internal class InMemoryDeviceScanSessionStore : DeviceScanSessionStore {

    @Volatile
    override var deviceMetrics: DeviceMetrics? = null

    @Volatile
    override var batterySnapshot: BatterySnapshot? = null

    @Volatile
    override var runningApps: ImmutableList<RunningApp>? = null

    override fun clear() {
        deviceMetrics = null
        batterySnapshot = null
        runningApps = null
    }
}
