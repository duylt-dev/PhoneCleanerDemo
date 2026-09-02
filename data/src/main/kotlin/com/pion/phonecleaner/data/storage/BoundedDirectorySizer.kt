package com.pion.phonecleaner.data.storage

import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.core.common.result.asFailure
import com.pion.phonecleaner.core.common.result.asSuccess
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.repository.DirectorySizer
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * "How big is this subtree" — the junk badge, an app's residual folder, a WhatsApp bucket.
 *
 * It replaces `xc.c` (`java/xc/c.java:15-35`), which `docs/system-architecture.md` §4.5 uses as the
 * example of what an unbounded walk costs: **no depth cap, no visited-inode set, no time cap and no
 * symlink guard**. A single symlink loop makes it recurse until the stack goes.
 *
 * "Bounded" is not decoration and it is not this class's own cleverness — every bound lives on the
 * [WalkConfig] the caller passes, and the traversal is the shared [walkFilesBounded]. A caller that
 * wants a cheap estimate passes a small `maxDepth` and a `timeLimit`; a caller that wants an exact
 * figure passes a large one. **This is the same walk the scanner uses**, so the badge and the scan
 * can never disagree about what is in scope.
 *
 * A truncated walk returns the bytes it did see. That is honest only because the caller chose the
 * bound — the caller is the one that knows whether to label the number as an estimate.
 */
internal class BoundedDirectorySizer(
    private val dispatchers: DispatcherProvider,
) : DirectorySizer {

    override suspend fun sizeOf(config: WalkConfig): AppResult<Long> =
        withContext(dispatchers.io) {
            try {
                var total = 0L
                walkFilesBounded(config.roots, config) { total += it.length() }
                total.asSuccess()
            } catch (e: IOException) {
                AppError.Storage(cause = e.message).asFailure()
            } catch (e: SecurityException) {
                AppError.PermissionDenied(e.message).asFailure()
            }
        }
}
