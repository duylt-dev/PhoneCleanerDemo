package com.pion.phonecleaner.data.app

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.domain.model.app.AppStorageStats
import com.pion.phonecleaner.domain.model.app.InstalledApp
import com.pion.phonecleaner.domain.repository.AppStorageStatsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * `StorageStatsManager.queryStatsForUid`, once per app, bounded
 * (`docs/screens/14-file-tools-and-app-manager.md` §5.2).
 *
 * **All three numbers come out of one round trip.** The competitor declares `dataBytes` and
 * `cacheBytes` on its row model and never writes either, then reports the APK's own length as the
 * app's size — so an app with a 2 GB data directory is listed at 25 MB (§5.5).
 *
 * **The parallelism is here, not at the call site** (`LLM.md` §6.5). Eight permits, the same count as
 * the competitor's `Semaphore(8)`, expressed once instead of at each call site — it is that call-site
 * freedom that put one of its five scanners on `Default` while the other four ran on IO.
 *
 * ### One thing §5.2 gets wrong about the platform, and how this handles it
 *
 * The appendix says *"sizes and uninstall need no special access"*. `queryStatsForUid` for **another
 * app's** uid requires `PACKAGE_USAGE_STATS`, which is a user-granted special access
 * (PENDING OWNER DECISION 3). Without it every call throws `SecurityException`. That is caught per
 * app and emitted as null sizes, which the row renders as `—`: a measurement that did not happen is
 * not a zero, and the competitor's `1000L` default sorts and reads as if it were real.
 */
internal class StorageStatsAppRepository(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
) : AppStorageStatsRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun statsFor(apps: List<InstalledApp>): Flow<AppStorageStats> = flow {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        if (manager == null) {
            apps.forEach { emit(AppStorageStats(it.packageName)) }
            return@flow
        }
        val permits = Semaphore(MAX_CONCURRENT_QUERIES)
        val results = Channel<AppStorageStats>(Channel.BUFFERED)
        coroutineScope {
            launch {
                coroutineScope {
                    // Structural children of this scope: cancelling the collector cancels every
                    // outstanding query, and none of them is a field anyone must remember to cancel.
                    apps.forEach { app ->
                        launch { permits.withPermit { results.send(measure(manager, app)) } }
                    }
                }
                results.close()
            }
            for (stats in results) emit(stats)
        }
    }.buffer().flowOn(dispatchers.io)

    private fun measure(manager: StorageStatsManager, app: InstalledApp): AppStorageStats =
        runCatching {
            val stats = manager.queryStatsForUid(StorageManager.UUID_DEFAULT, app.uid)
            AppStorageStats(
                packageName = app.packageName,
                appBytes = stats.appBytes,
                dataBytes = stats.dataBytes,
                cacheBytes = stats.cacheBytes,
            )
        }.getOrElse {
            // SecurityException (no usage access), IllegalArgumentException (uid gone mid-scan), or
            // an OEM provider refusing. Null sizes, never a fabricated one.
            AppStorageStats(app.packageName)
        }

    private companion object {
        /** The competitor's `Semaphore(8)`, stated once rather than per call site. */
        const val MAX_CONCURRENT_QUERIES = 8
    }
}
