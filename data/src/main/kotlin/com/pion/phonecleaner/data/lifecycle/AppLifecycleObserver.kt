package com.pion.phonecleaner.data.lifecycle

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **One answer to "is the app in front".** The competitor has four, and they can disagree
 * (`docs/system-architecture.md` §5.4):
 *
 * - `od.h0` — `ActivityLifecycleCallbacks` plus a raw, unsynchronised `Stack<Activity>`
 * - `zd.c` — a hand-rolled `ArrayList<Activity>` used as a back stack
 * - `od.p0.u(ctx)` — `ActivityManager.getRunningAppProcesses()`, called on a broadcast thread
 * - `wd.r0.o(app)` — a fourth, in the notification façade
 *
 * Four sources for one boolean is four chances for an out-of-app surface to fire while the user is
 * looking at the app. This is one `StateFlow<Boolean>`, from the platform's own process lifecycle,
 * declared `single` in `coreDataModule`.
 *
 * It holds **no `Activity` reference**. `EstissueActivity.INSTANCE` — a static `Activity` used as the
 * app-wide ad host — is the defect this shape exists to make impossible (§5.10).
 */
internal class AppLifecycleObserver : DefaultLifecycleObserver {

    private val foreground = MutableStateFlow(false)

    /** True between `ON_START` and `ON_STOP` of the process lifecycle. */
    val isInForeground: StateFlow<Boolean> = foreground.asStateFlow()

    init {
        // ProcessLifecycleOwner must be observed from the main thread, and Koin builds a `single` on
        // whichever thread asks first — which, per §5.2, can be a BroadcastReceiver's. Posting keeps
        // that from being an IllegalStateException on a thread nothing wraps: the same failure mode
        // §5.2 records for reading the log gate through DI.
        onMainThread { ProcessLifecycleOwner.get().lifecycle.addObserver(this) }
    }

    override fun onStart(owner: LifecycleOwner) {
        foreground.value = true
    }

    override fun onStop(owner: LifecycleOwner) {
        foreground.value = false
    }

    private fun onMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else MAIN.post { block() }
    }

    private companion object {
        val MAIN = Handler(Looper.getMainLooper())
    }
}
