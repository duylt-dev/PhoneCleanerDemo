package com.pion.phonecleaner.data.files

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.domain.model.file.ScanCoverage
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.model.file.WhatsAppBucket
import com.pion.phonecleaner.domain.model.file.WhatsAppBucketId
import com.pion.phonecleaner.domain.model.file.WhatsAppScanProgress
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import com.pion.phonecleaner.domain.repository.WhatsAppRoots
import com.pion.phonecleaner.domain.repository.WhatsAppScanner
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList

/**
 * The WhatsApp bucket scan, built out of the **one** walker in the app
 * (`docs/screens/14-file-tools-and-app-manager.md` §6.2).
 *
 * The competitor's `Vacatur.s()` is a second hand-written recursive `listFiles()` walk with no depth
 * cap, no symlink guard, no exclusions and no cancellation — a sixth scanner family beside the five
 * that already share no code. It is deleted: this composes `StorageScanner` with a bounded
 * `WalkConfig`, so every bound the app has is applied here too, and cancelling the collector stops
 * the walk at the next directory.
 *
 * **Roots are resolved, never literal.** `StorageRootProvider` is the only object that knows which
 * roots are readable right now, and [WhatsAppRoots] hands out relative suffixes that are joined to
 * them. The legacy `/WhatsApp` tree is reachable only when the user has granted it through SAF, which
 * is why `Finished` carries the coverage rather than implying the six buckets are the whole story
 * (§10 item 1 — the per-API reachability matrix is design intent, confidence medium, unverified).
 *
 * DECLARED IN `filesDataModule`; `internal`.
 */
internal class DefaultWhatsAppScanner(
    private val scanner: StorageScanner,
    private val roots: WhatsAppRoots,
    private val rootProvider: StorageRootProvider,
    private val dispatchers: DispatcherProvider,
) : WhatsAppScanner {

    override fun scan(): Flow<WhatsAppScanProgress> = flow {
        val readable = (rootProvider.readableRoots() as? AppResult.Success)?.value ?: persistentListOf()
        for (bucket in WhatsAppBucketId.entries) {
            emit(WhatsAppScanProgress.BucketFinished(scanBucket(bucket, readable)))
        }
        emit(
            WhatsAppScanProgress.Finished(
                coverage = ScanCoverage(
                    surfaces = (rootProvider.coveredSurfaces() as? AppResult.Success)?.value
                        ?: persistentListOf(),
                    // The legacy root is a SAF opt-in and `Android/data` is unreadable on API 30+
                    // whatever is granted, so this scan is partial by construction (§0.2).
                    isPartial = true,
                ),
            ),
        )
    }.flowOn(dispatchers.io)

    private suspend fun scanBucket(
        bucket: WhatsAppBucketId,
        readableRoots: List<String>,
    ): WhatsAppBucket {
        val suffixes = roots.suffixesFor(bucket)
        if (suffixes.isEmpty() || readableRoots.isEmpty()) {
            return WhatsAppBucket(bucket, persistentListOf())
        }
        val paths = readableRoots.flatMap { root -> suffixes.map { join(root, it) } }
        val files = scanner
            .walk(WalkConfig(roots = paths.toImmutableList(), maxDepth = MAX_DEPTH))
            .toList()
        return WhatsAppBucket(
            id = bucket,
            files = files.distinctBy(ScannedFile::path).toImmutableList(),
        )
    }

    /** A `content://` tree root is not a filesystem path, so a suffix cannot be appended to one. */
    private fun join(root: String, suffix: String): String =
        if (root.startsWith(TREE_URI_PREFIX)) root else "${root.trimEnd('/')}/${suffix.trimStart('/')}"

    private companion object {
        const val TREE_URI_PREFIX = "content://"

        /**
         * UNKNOWN — no source states a depth for this walk. A media folder tree is two or three
         * levels below its bucket root; six bounds a pathological one without truncating a real one.
         */
        const val MAX_DEPTH = 6
    }
}
