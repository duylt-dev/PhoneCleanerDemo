package com.pion.phonecleaner.feature.antivirus.scan

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Re-reports the two gate inputs whenever the default network changes.
 *
 * Without it the network gate is a dead end that `ON_START` cannot clear: turning Wi-Fi on from the
 * notification shade does not stop the activity, so nothing would re-enter the reducer and the
 * screen would sit on "no connection" over a working connection until the user left and came back.
 * That is the shape of defect `LLM.md` §7.4 is written against, one layer down.
 *
 * Platform truth, living in the composable and reporting upward as an Intent (MVI §4). It registers
 * on composition and **unregisters in `onDispose`** — unlike the competitor's result-screen
 * receiver, which is registered per visit and never unregistered.
 *
 * The callbacks arrive on a framework thread. Every path they reach is safe there: `onIntent` only
 * writes a `MutableStateFlow` through `setState` and starts work on `viewModelScope`, which
 * dispatches to main itself.
 */
@Composable
internal fun NetworkStateEffect(onIntent: (AntivirusScanIntent) -> Unit) {
    val context = LocalContext.current
    val handler by rememberUpdatedState(onIntent)
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = report()
            override fun onLost(network: Network) = report()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = report()

            // Re-read from the manager rather than from the callback's own argument: the gate is
            // about the DEFAULT network being usable, which one callback's `network` is not.
            private fun report() = handler(
                AntivirusScanIntent.GateStateReported(
                    hasStorageAccess = hasAnyStorageRead(context),
                    isOnline = isOnline(context),
                ),
            )
        }
        manager?.registerDefaultNetworkCallback(callback)
        onDispose { manager?.unregisterNetworkCallback(callback) }
    }
}
