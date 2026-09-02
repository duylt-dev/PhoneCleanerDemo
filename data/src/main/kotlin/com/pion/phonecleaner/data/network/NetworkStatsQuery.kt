package com.pion.phonecleaner.data.network

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager

/**
 * The one call into `NetworkStatsManager`, isolated so the repository above it is pure bookkeeping.
 *
 * **Nothing is swallowed here.** `querySummary` throws `SecurityException` when usage access is not
 * granted and `RemoteException` when `system_server` is unreachable; both propagate to
 * [NetworkStatsTrafficRepository], which is the layer that knows how to describe them. The
 * competitor's equivalent wraps every one of these lines in `catch (Exception unused) {}`, so a
 * refused query and a quiet month look identical
 * (`docs/reverse-engineering/19-network-and-speed-test.md` :519-521).
 *
 * `subscriberId` is `null`: reading it needs `READ_PHONE_STATE`, which this app does not hold and
 * does not want. On a multi-SIM device the mobile bucket is therefore whatever the platform reports
 * for a null subscriber — the same limit the competitor has, stated rather than hidden (:514).
 *
 * `rxBytes` and `txBytes` are summed, because the split this screen offers is mobile-versus-Wi-Fi
 * and never download-versus-upload (:516).
 */
internal object NetworkStatsQuery {

    fun bytesByUid(
        manager: NetworkStatsManager,
        networkType: Int,
        startMillis: Long,
        endMillis: Long,
    ): Map<Int, Long> {
        val stats: NetworkStats = manager.querySummary(networkType, null, startMillis, endMillis)
        val totals = LinkedHashMap<Int, Long>()
        try {
            // ONE reused Bucket, as the platform intends: getNextBucket fills it in place.
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                if (!stats.getNextBucket(bucket)) break
                val bytes = bucket.rxBytes + bucket.txBytes
                if (bytes > 0L) totals[bucket.uid] = (totals[bucket.uid] ?: 0L) + bytes
            }
        } finally {
            stats.close()
        }
        return totals
    }
}
