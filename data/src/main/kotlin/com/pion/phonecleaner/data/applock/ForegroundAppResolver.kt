package com.pion.phonecleaner.data.applock

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The three platform reads `UsageStatsForegroundAppMonitor` needs, in one place: is usage access
 * granted, is the screen on, and what is in the foreground.
 *
 * Split from the monitor so the loop's *policy* — the gates, the dedup, the unlock session — is
 * testable without a `Context`, and so the one place that can throw `SecurityException` is one file.
 *
 * DECLARED IN `appLockDataModule`; no other cluster names this type.
 */
internal class ForegroundAppResolver(private val context: Context) {

    /** Our own package, so the lock surface can never ask to lock the app drawing it. */
    val selfPackage: String = context.packageName

    /**
     * Whether `PACKAGE_USAGE_STATS` is granted.
     *
     * `:data`'s manifest declares `<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" />`,
     * so the grant is reachable — the system's Usage Access page lists only apps that declare it.
     * Declared is not granted: the user gives this one by hand, and until they do this returns
     * `false` and the monitor idles instead of throwing. App Lock's appendix models the grant as
     * something the user gives from inside the App Lock flow; the declaration does not change that
     * flow, it only makes the page it sends them to able to show this app.
     */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        // `unsafeCheckOpNoThrow` is API 29; `minSdk` is 28, so the deprecated spelling is the only
        // one that exists on the floor of our own support range (LLM.md §10.1).
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * `true` while the screen is interactive.
     *
     * The competitor polls identically foregrounded, backgrounded and with the screen off: no
     * backoff, no doze awareness, no `PowerManager` interaction, no jitter
     * (`java/od/e0.java:198-274`). Gating the loop on this is the single change that stops a 500 ms
     * wakeup running all night.
     *
     * `callbackFlow` + `awaitClose`, so the receiver is unregistered when the collector is
     * cancelled — the competitor's watchdog registers nothing and unregisters nothing.
     */
    fun screenState(): Flow<Boolean> = callbackFlow {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        trySend(power?.isInteractive ?: true)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> trySend(true)
                    Intent.ACTION_SCREEN_OFF -> trySend(false)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    /**
     * The most recent package moved to the foreground in `(fromMillis, toMillis]`, or `null`.
     *
     * **The window is the caller's, and it moves.** `od.o0.g()` queries `now − 3 600 000` to `now`
     * on *every* 500 ms tick (`java/od/o0.java:99-114`) — an hour of usage events walked 120 times a
     * minute, with no cursor and no cache. The hour is not even its own constant: it is
     * `com.thinkup.core.common.om.n.f39305m`, borrowed from an ad SDK.
     *
     * **It catches.** `od.o0.g()` has no `try`/`catch`, so a `SecurityException` after a runtime
     * revocation propagates into the polling coroutine and kills the job silently, permanently, with
     * no user-visible signal. `null` here is "nothing resolved", and the caller re-checks the grant.
     */
    fun foregroundPackage(fromMillis: Long, toMillis: Long): String? {
        val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        return try {
            val events = usage.queryEvents(fromMillis, toMillis)
            val event = UsageEvents.Event()
            var latest: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    latest = event.packageName
                }
            }
            latest
        } catch (revoked: SecurityException) {
            // Reported by returning null; the monitor re-checks the grant rather than dying.
            null
        }
    }
}
