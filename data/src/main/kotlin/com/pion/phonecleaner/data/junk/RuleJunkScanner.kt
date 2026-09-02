package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.domain.model.junk.JunkCategory
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.domain.repository.DirectorySizer
import com.pion.phonecleaner.domain.repository.JunkRuleCatalog
import com.pion.phonecleaner.domain.repository.JunkScanner
import com.pion.phonecleaner.domain.repository.StorageRootProvider
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * The junk engine: three passes over the three shared file primitives, behind one cold `Flow`.
 *
 * It replaces `xc.x` (296 L), `xc.j` (the sizer and the unbounded parallel walker), `xc.c` (the
 * unbounded recursive size) and `xc.z` (the storage root cached in a static forever, with a
 * hard-coded `/storage/emmc/` override, so a removable SD card is never scanned)
 * (`docs/screens/12-junk-cleaning.md` §7.3).
 *
 * **It owns no walk.** [StorageRootProvider] says what is readable right now — re-read per scan, not
 * cached in a static (Delta S10) — [DirectorySizer] sizes a rule's folder under `WalkConfig`'s
 * bounds, and [StorageScanner] performs the one tree walk this cluster needs. That is what makes the
 * storage branch a `storageDataModule` swap rather than a rewrite of this class
 * (`docs/system-architecture.md` §8.4).
 *
 * `flowOn(dispatchers.io)`: the dispatcher is chosen **inside** the repository, never by the caller
 * (`LLM.md` §6.5). No screen, ViewModel or use case in this cluster names a dispatcher.
 */
internal class RuleJunkScanner(
    private val catalog: JunkRuleCatalog,
    private val roots: StorageRootProvider,
    private val sizer: DirectorySizer,
    private val scanner: StorageScanner,
    private val dispatchers: DispatcherProvider,
) : JunkScanner {

    override fun scan(): Flow<ScanProgress> = flow {
        val readable = roots.readableRoots().getOrNull().orEmpty()
        // Passes 1 and 2 resolve a rule fragment under a root, which only a filesystem path can do.
        // A granted SAF tree is a `content://` URI and is reachable only through the walk in pass 3,
        // which `StorageScanner` already handles for both shapes.
        val directories = readable.filterNot { it.startsWith(TREE_URI_PREFIX) }

        val categories = ArrayList<JunkCategory>(PASS_COUNT)
        finishPass(systemCachePass(catalog.systemCacheRules(), directories, sizer), categories)
        finishPass(appResidualPass(catalog.appRules(), directories, sizer), categories)
        finishPass(apkPass(readable, scanner), categories)

        emit(
            ScanProgress.Finished(
                categories = categories.toImmutableList(),
                totalBytes = categories.sumOf { it.totalBytes },
            ),
        )
    }.flowOn(dispatchers.io)

    /**
     * `PassFinished` is emitted for every pass, including an empty one — a category appearing the
     * moment its pass ends is the whole point of the emission, and the competitor's own
     * `listener.c(category, result)` has an **empty body** in its only implementation, so the
     * information is produced and thrown away (§1.2).
     */
    private suspend fun FlowCollector<ScanProgress>.finishPass(
        category: JunkCategory?,
        into: MutableList<JunkCategory>,
    ) {
        if (category != null) into += category
        emit(ScanProgress.PassFinished(category))
    }

    private companion object {
        const val TREE_URI_PREFIX = "content://"

        /** Three, and `JunkScanState.progress` divides by the same three (§3.1). */
        const val PASS_COUNT = 3
    }
}
