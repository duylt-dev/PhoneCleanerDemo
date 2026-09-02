package com.pion.phonecleaner.core.ui.icon

import android.content.Context
import android.content.pm.PackageManager
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options

/**
 * The Coil `Fetcher` behind [AppIconLoader]: `packageName` in, the installed app's icon out.
 *
 * It is a fetcher rather than a suspending helper so that Coil's own machinery does the work the
 * competitor hand-rolled — a memory cache, cancellation when the row scrolls away mid-load, and a
 * placeholder while it runs. `cd.g` instead runs a 2-thread pool that no composition can cancel, and
 * `vd.d.a()` calls `loadIcon` for **every** launchable app before anything renders, so the first frame
 * waits on N `PackageManager` icon loads (`docs/screens/17` §2.2).
 *
 * `getApplicationIcon` is a blocking binder call, which is why it belongs inside `fetch()`: Coil runs
 * fetchers on its own IO dispatcher.
 *
 * A missing package returns `null`, not an exception. An app can be uninstalled between the moment the
 * repository listed it and the moment its row scrolls into view, and a row that draws nothing is the
 * correct outcome — Coil then falls back to whatever `error`/`fallback` the caller set.
 */
class PackageIconFetcher(
    private val request: AppIconLoader.Request,
    private val packageManager: PackageManager,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val icon = try {
            packageManager.getApplicationIcon(request.packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return ImageFetchResult(
            image = icon.asImage(),
            isSampled = false,
            // The icon comes from the package's own resources on disk, never the network.
            dataSource = DataSource.DISK,
        )
    }

    class Factory(context: Context) : Fetcher.Factory<AppIconLoader.Request> {
        private val packageManager = context.applicationContext.packageManager

        override fun create(
            data: AppIconLoader.Request,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = PackageIconFetcher(data, packageManager)
    }

    /**
     * The memory-cache key. Without one Coil has nothing to cache a request under, so a list that
     * scrolls back up would re-load every icon it already had.
     */
    object CacheKeyer : Keyer<AppIconLoader.Request> {
        override fun key(data: AppIconLoader.Request, options: Options): String =
            "app-icon:${data.packageName}"
    }
}
