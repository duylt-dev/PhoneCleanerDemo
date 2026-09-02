package com.pion.phonecleaner.data.app

import android.content.Context
import android.content.pm.PackageManager
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.domain.repository.AppControlRepository
import kotlinx.coroutines.withContext

/**
 * *"Is this package installed right now?"* — asked of `PackageManager`, not inferred from a broadcast
 * (`docs/screens/14-file-tools-and-app-manager.md` §5.5, §6.2).
 *
 * Two screens need it and both were getting a wrong answer in the competitor:
 *
 *  * the App Manager counts a removal complete from **every** `PACKAGE_REMOVED` broadcast without
 *    inspecting `EXTRA_REPLACING`, so a background update advances its counter and a re-install is
 *    counted as a removal;
 *  * the WhatsApp cleaner never asks at all, and renders six zero-byte tiles on a device that has
 *    never had WhatsApp installed.
 *
 * `getPackageInfo` is the query, not `getInstalledApplications().any { }`: the second enumerates a
 * few hundred packages to answer a question about one, and on API 30+ package visibility filtering
 * can hide the very package being asked about from the list while still answering a direct query.
 *
 * DECLARED IN `filesDataModule`; the class is `internal`, so no other cluster can declare it.
 */
internal class PackageManagerAppControlRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : AppControlRepository {

    override suspend fun isInstalled(packageName: String): Boolean = withContext(dispatchers.io) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
