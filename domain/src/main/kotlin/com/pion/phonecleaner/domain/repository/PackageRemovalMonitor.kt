package com.pion.phonecleaner.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Package names as the system reports them removed (`docs/screens/15-antivirus.md` §2.3).
 *
 * It replaces an anonymous `BroadcastReceiver` that the competitor's result screen **registers and
 * never unregisters** — one leaked receiver per visit — with no export flag, and whose handler
 * launches on a fresh `CoroutineScope(Dispatchers.Main)` that nothing cancels
 * (`docs/reverse-engineering/15-antivirus.md` §3.4).
 *
 * Cold: each collection registers, and `awaitClose` unregisters. The screen collects it under
 * `repeatOnLifecycle(STARTED)` and reports upward as an Intent — a broadcast is platform truth, and
 * platform truth lives in the composable (MVI §4).
 *
 * The emission is a **package name**, and the ViewModel matches it against the list. The competitor
 * compares nothing and removes whatever its last-tapped field holds, so uninstalling any app
 * anywhere deletes the wrong row.
 */
interface PackageRemovalMonitor {
    fun removals(): Flow<String>
}
