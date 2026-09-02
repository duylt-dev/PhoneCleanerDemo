package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.device.BatterySnapshot
import kotlinx.coroutines.flow.Flow

/**
 * The battery, as a feed (`docs/screens/18-device-battery-and-apps.md` §1.1, §5.2). Replaces `cd.c`,
 * its `BroadcastReceiver` and both `ContentObserver`s; declared once, in `deviceDataModule` (§8).
 *
 * **Observe, don't fetch.** The same `Flow` feeds `batteryinfo` and the battery card on
 * `devicestatusdetail`, so the card ticks live on both — which the competitor's snapshot-in-`onCreate`
 * detail screen does not do.
 *
 * The implementation owns the platform: one `callbackFlow` registers the receiver and the two
 * observers and tears all three down in `awaitClose`, so there is no path where a receiver outlives
 * the screen. The competitor's two bare `try`/`catch (Exception)` blocks around `unregisterReceiver`
 * exist to paper over exactly that.
 */
interface BatteryRepository {
    fun observe(): Flow<BatterySnapshot>
}
