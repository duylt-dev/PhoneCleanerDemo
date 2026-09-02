package com.pion.phonecleaner.feature.antivirus.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * The two platform reads behind [AntivirusScanIntent.GateStateReported].
 *
 * They live beside the Route rather than inside the ViewModel because both are platform truth, and
 * platform truth lives in the composable and reports upward (MVI §4). `AntivirusScanViewModel`
 * imports no `android.*` type at all — the competitor re-implements both checks in *two* different
 * callers, so a deep link reaches neither (`docs/screens/15-antivirus.md` §1).
 *
 * They are plain functions, not a Koin binding: they take a `Context` and hold nothing.
 */

/**
 * The read permissions the DEFAULT storage branch asks for, identical to the junk cluster's — one
 * shape of storage ask across the app.
 *
 * `MANAGE_EXTERNAL_STORAGE` is deliberately not among them: it is never assumed grantable
 * (`docs/system-architecture.md` §8.1, owner decision), and refusing it is not a dead end here —
 * the scan runs with [com.pion.phonecleaner.domain.model.security.ScanCoverage.InstalledAppsOnly]
 * and the screen says what was skipped.
 */
internal fun storageReadPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

internal fun hasAnyStorageRead(context: Context): Boolean =
    storageReadPermissions().any { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

/**
 * `true` only when a network is present **and validated**.
 *
 * `NET_CAPABILITY_VALIDATED` is checked as well as `INTERNET` because a captive-portal Wi-Fi
 * reports the second and not the first, and a scan started on one fails a minute later with a
 * timeout instead of the one thing the user can act on. Requires `ACCESS_NETWORK_STATE`, declared
 * by this module's own manifest beside this call.
 */
internal fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}
