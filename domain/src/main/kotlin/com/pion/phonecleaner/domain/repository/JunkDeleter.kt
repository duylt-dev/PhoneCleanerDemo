package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.junk.CleanProgress
import kotlinx.coroutines.flow.Flow

/**
 * The component that carries all the platform risk (`docs/screens/12-junk-cleaning.md` §5.4).
 *
 * It **composes** [FileDeleter] rather than re-implementing a delete, and reports a
 * `CleanProgress.Failed(path, reason)` for every path a strategy could not remove. The competitor's
 * whole storage strategy is `FilesKt.deleteRecursively(File(path))` wrapped in
 * `catch (Exception) { printStackTrace() }` returning `0L`, behind `MANAGE_EXTERNAL_STORAGE`
 * (`MenaremovActivity.java:242`) — the most powerful permission the platform offers, which on
 * API 30+ still cannot open `Android/data` or `Android/obb`, where a modern app's leftovers actually
 * are (Delta C6).
 *
 * **`MANAGE_EXTERNAL_STORAGE` is never assumed grantable** (`docs/system-architecture.md` §8.1). The
 * fallback order is stated in §5.4 and in the implementation; the permission is not assumed, and
 * which of the four strategies is viable for a Play-distributed build is a policy question the
 * corpus cannot settle (§8.4 item 2).
 *
 * DECLARED IN `junkDataModule`.
 */
interface JunkDeleter {

    /**
     * One emission per path, then exactly one `CleanProgress.Finished`.
     *
     * The parameter is a plain `Set` because it is an input the caller already owns; the
     * `Immutable*` rule governs what a repository **hands out** (`LLM.md` §8).
     */
    fun delete(paths: Set<String>): Flow<CleanProgress>
}
