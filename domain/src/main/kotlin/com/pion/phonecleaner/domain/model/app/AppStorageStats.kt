package com.pion.phonecleaner.domain.model.app

/**
 * What one app occupies on disk, as `StorageStatsManager.queryStatsForUid` reports it.
 *
 * **All three numbers, not one.** The competitor declares `dataBytes` and `cacheBytes` on its row
 * model, never writes them and never reads them, and renders the APK's own length as the app's size —
 * so every app with a large data directory is understated
 * (`docs/screens/14-file-tools-and-app-manager.md` §5.5). `queryStatsForUid` returns all three in one
 * round trip; reporting one of them is a choice, not a saving.
 *
 * Each is **nullable, and null means "not measured"** — the competitor defaults its size field to
 * `1000L`, so a query that failed renders as *"1000 B"*, sorts as a real value and reads as truth.
 */
data class AppStorageStats(
    val packageName: String,
    val appBytes: Long? = null,
    val dataBytes: Long? = null,
    val cacheBytes: Long? = null,
) {
    val totalBytes: Long get() = (appBytes ?: 0L) + (dataBytes ?: 0L) + (cacheBytes ?: 0L)
    val isMeasured: Boolean get() = appBytes != null
}
