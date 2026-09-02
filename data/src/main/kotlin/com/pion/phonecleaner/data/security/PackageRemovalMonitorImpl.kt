package com.pion.phonecleaner.data.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.pion.phonecleaner.domain.repository.PackageRemovalMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * `ACTION_PACKAGE_REMOVED`, as a cold flow (`docs/screens/15-antivirus.md` §2.3).
 *
 * Three defects of the competitor's anonymous receiver are closed by the shape alone
 * (`docs/reverse-engineering/15-antivirus.md` §3.4):
 *
 *  1. it is **registered and never unregistered** — one leak per visit to the result screen. Here
 *     `awaitClose` unregisters, and the screen collects under `repeatOnLifecycle(STARTED)`.
 *  2. it is registered with **no export flag**. `ContextCompat.registerReceiver` with
 *     `RECEIVER_NOT_EXPORTED` is required on API 34+ for a runtime receiver, and is correct on every
 *     level: nothing outside this process has any business reaching it.
 *  3. its handler launches on a fresh `CoroutineScope(Dispatchers.Main)` that nothing cancels. Here
 *     the emission is on the collector's scope.
 *
 * **A replacement is not a removal.** `EXTRA_REPLACING` is set when a package is being upgraded, and
 * treating that as a removal would silently drop a finding the user never acted on.
 */
internal class PackageRemovalMonitorImpl(
    private val context: Context,
) : PackageRemovalMonitor {

    override fun removals(): Flow<String> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                // The package name is in the data URI's scheme-specific part, e.g.
                // `package:com.example`. It is the only payload; the competitor reads none of it.
                intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() }?.let(::trySend)
            }
        }
        val filter = IntentFilter(Intent.ACTION_PACKAGE_REMOVED).apply { addDataScheme("package") }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
