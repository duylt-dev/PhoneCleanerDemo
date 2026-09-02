package com.pion.phonecleaner.feature.antivirus.result

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pion.phonecleaner.domain.repository.PackageRemovalMonitor
import org.koin.compose.koinInject

/**
 * Turns `PACKAGE_REMOVED` broadcasts into Intents (`docs/screens/15-antivirus.md` §2.3).
 *
 * It replaces an anonymous `BroadcastReceiver` that the competitor's result screen **registers and
 * never unregisters** — one leaked receiver per visit — with no export flag, and whose handler
 * launches on a fresh `CoroutineScope(Dispatchers.Main)` that nothing cancels. Here the receiver's
 * whole lifetime is `PackageRemovalMonitorImpl`'s `callbackFlow`, and this collector is a child of
 * the composition's scope under `repeatOnLifecycle(STARTED)`.
 *
 * The monitor is `koinInject()`ed **into the composable**, never into a ViewModel: a broadcast is
 * platform truth, and platform truth lives here and reports upward (MVI §4).
 *
 * KNOWN GAP, stated rather than papered over: while the system uninstall dialog is in front, this
 * screen is stopped and the collector is not registered, so the broadcast for the app the user just
 * uninstalled can land in that window and be missed. `UninstallReturned` then lowers the row's
 * in-flight flag rather than deleting the row — a row that is still listed after a successful
 * uninstall is a stale row until the next check, whereas removing one on an unverified assumption
 * is the competitor's defect. Closing it properly needs an installation re-check on return, which
 * needs package visibility this module does not declare (see its manifest).
 */
@Composable
internal fun PackageRemovalEffect(onIntent: (AntivirusResultIntent) -> Unit) {
    val monitor = koinInject<PackageRemovalMonitor>()
    val lifecycleOwner = LocalLifecycleOwner.current
    val handler by rememberUpdatedState(onIntent)
    LaunchedEffect(monitor, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            monitor.removals().collect { packageName ->
                handler(AntivirusResultIntent.PackageRemoved(packageName))
            }
        }
    }
}
