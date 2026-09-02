package com.pion.phonecleaner.data.applock

import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.time.AppClock
import com.pion.phonecleaner.domain.repository.AppLockPinRepository
import com.pion.phonecleaner.domain.repository.AppLockRepository
import com.pion.phonecleaner.domain.repository.AppLockSettingsRepository
import com.pion.phonecleaner.domain.repository.ForegroundAppMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The App Lock watchdog. Replaces `od.e0` (the poller) plus `od.o0.g()` (the resolver).
 *
 * DECLARED IN `appLockDataModule`. It runs on the injected `named("appScope")` scope, so it is a
 * child of something with an owner — `od.e0.d()` launches on `ob.l0.a(z0.b())`, a `CoroutineScope`
 * created inline at the call site, a child of nothing, cancellable only through a static boolean
 * (`java/od/e0.java:171-180`).
 *
 * ### Two pending owner decisions, neither closed here
 *
 * **(3) `PACKAGE_USAGE_STATS`** — see `ForegroundAppResolver.hasUsageAccess`. Missing grant means
 * this monitor idles and logs; it never throws and never claims to be watching.
 *
 * **(4) reboot survival and out-of-app assertiveness** — there is **no `BootCompletedReceiver` and
 * no `RECEIVE_BOOT_COMPLETED`**, so App Lock stops at every process death and after every reboot
 * until the user next opens the app. That is `docs/system-architecture.md` §10.1 P6, an owner fork,
 * and it is left open on purpose. What this class does about it is make the fix *one class away*:
 * [start] is idempotent, takes no argument and re-derives every gate from persisted state, so a
 * receiver would call `get<ForegroundAppMonitor>().start()` and nothing else would change.
 */
internal class UsageStatsForegroundAppMonitor(
    private val resolver: ForegroundAppResolver,
    private val appLock: AppLockRepository,
    private val settings: AppLockSettingsRepository,
    private val pin: AppLockPinRepository,
    private val clock: AppClock,
    private val log: AppLogger,
    private val scope: CoroutineScope,
) : ForegroundAppMonitor {

    /**
     * `Channel(BUFFERED)` + `receiveAsFlow`, the same shape as `MviViewModel`'s effects: buffered,
     * delivered exactly once, **never replayed**. A replay would re-raise the lock surface over
     * whatever the user moved to next.
     */
    private val requests = Channel<String>(Channel.BUFFERED)
    override val lockRequests: Flow<String> = requests.receiveAsFlow()

    private var job: Job? = null

    /** The last package seen in the foreground. Dedup only — it is NOT the unlock model. */
    private var currentForeground: String? = null

    /**
     * The explicit unlock record `docs/screens/16-app-lock.md` §3.5 asks for.
     *
     * The competitor has no such thing: "unlocked" is an accident of `od.e0`'s single static
     * last-package-seen field, so nobody can read the rule off the code and a monitor restart
     * silently re-locks or fails to re-lock.
     */
    private var unlock: UnlockSession? = null

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            combine(
                settings.observeSettings(),
                appLock.lockedPackages(),
                pin.isPinSet(),
            ) { current, locked, pinSet ->
                Gate(
                    enabled = current.isAppLockEnabled && pinSet && locked.isNotEmpty(),
                    locked = locked,
                )
            }
                .distinctUntilChanged()
                // `collectLatest` is correct HERE and only here: a new gate must replace the loop
                // running under the old one. The ban on `collectLatest` is about screen Effects,
                // where it silently drops one of two navigations raised in the same frame.
                .collectLatest { gate -> watch(gate) }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
        currentForeground = null
        unlock = null
    }

    override fun noteUnlocked(packageName: String) {
        unlock = UnlockSession(packageName, clock.now().toEpochMilliseconds())
    }

    private suspend fun watch(gate: Gate) {
        if (!gate.enabled) return
        if (!resolver.hasUsageAccess()) {
            // PENDING OWNER DECISION (3). Logged, not silently swallowed, and not surfaced as a
            // notification either — `AppNotifier` is unbound and this cluster does not own it.
            log.e { "App Lock is on but usage access is not granted; the monitor is idle" }
            return
        }
        resolver.screenState().collectLatest { screenOn ->
            if (screenOn) poll(gate) else currentForeground = null
        }
    }

    /**
     * The loop. **It only runs while the screen is on**, and its query window moves.
     *
     * `od.o0.g()` re-queries a full hour of usage events on every 500 ms tick with no cursor
     * (`java/od/o0.java:104`). Here the window is `(lastQueryEnd, now]`, floored at
     * [MAX_WINDOW] so that a long screen-off gap cannot make one query walk a day of events.
     */
    private suspend fun poll(gate: Gate) {
        var lastQueryEnd = clock.now().toEpochMilliseconds() - FIRST_WINDOW.inWholeMilliseconds
        while (currentCoroutineContext().isActive) {
            val now = clock.now().toEpochMilliseconds()
            val from = maxOf(lastQueryEnd, now - MAX_WINDOW.inWholeMilliseconds)
            val resolved = resolver.foregroundPackage(from, now)
            if (resolved == null && !resolver.hasUsageAccess()) {
                log.e { "App Lock lost usage access at runtime; the monitor is idle" }
                return
            }
            if (resolved != null) {
                lastQueryEnd = now
                onForeground(resolved, gate)
            }
            delay(POLL_INTERVAL)
        }
    }

    private suspend fun onForeground(packageName: String, gate: Gate) {
        if (packageName == currentForeground) return
        currentForeground = packageName
        // The competitor's self-exemption is `contains`, so ANY package whose name contains its own
        // is exempt (java/od/e0.java:276-283). Ours is equality.
        if (packageName == resolver.selfPackage) return
        if (unlock?.packageName != packageName) unlock = null
        if (packageName !in gate.locked) return
        if (isStillUnlocked(packageName)) return
        try {
            requests.send(packageName)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (closed: Throwable) {
            log.e(closed) { "App Lock could not deliver a lock request for $packageName" }
        }
    }

    /**
     * The stated policy `docs/screens/16-app-lock.md` §3.5 asks for: **valid until the package
     * leaves the foreground, or [UNLOCK_TTL], whichever comes first.**
     *
     * The "leaves the foreground" half is [onForeground]'s `unlock = null`. The TTL is the backstop,
     * and its length is **UNKNOWN** — §3.5 writes "N minutes" and no source fixes N.
     */
    private fun isStillUnlocked(packageName: String): Boolean {
        val session = unlock ?: return false
        if (session.packageName != packageName) return false
        val age = clock.now().toEpochMilliseconds() - session.grantedAtMillis
        return age in 0..UNLOCK_TTL.inWholeMilliseconds
    }

    private class Gate(val enabled: Boolean, val locked: Set<String>)

    private class UnlockSession(val packageName: String, val grantedAtMillis: Long)

    private companion object {
        /** The competitor's own cadence (`java/od/e0.java:198-274`) — observed, not invented. */
        val POLL_INTERVAL: Duration = 500.milliseconds

        /** The first tick has no cursor, so it looks back a bounded distance rather than an hour. */
        val FIRST_WINDOW: Duration = 60.seconds

        /** The furthest back any one query reaches, however long the screen was off. */
        val MAX_WINDOW: Duration = 5.minutes

        /** UNKNOWN — see [isStillUnlocked]. */
        val UNLOCK_TTL: Duration = 5.minutes
    }
}
