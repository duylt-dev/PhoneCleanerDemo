package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * The duplicate engine. It **composes** [StorageScanner], [MediaStoreRepository] and [FileDigest] and
 * re-implements none of them (`docs/system-architecture.md` §4.5): five competitor scanner families
 * that share no code collapse into those three primitives, and an engine that walks its own tree is
 * how the sixth one came to exist.
 *
 * DECLARED IN `filesDataModule` as `Md5DuplicateFinder`.
 *
 * The pipeline it owns, in one place rather than the competitor's five stages spread across an
 * Activity and a ViewModel (`docs/screens/14-file-tools-and-app-manager.md` §2.2): collect the
 * surfaces the current grant reaches, dedupe by path, group by **exact byte size** keeping groups of
 * more than one, then digest only those candidates — a head digest first, a full digest only on a
 * head collision. The size pre-filter is what stops same-size-different-content files being read end
 * to end.
 */
interface DuplicateFinder {

    /**
     * Cold. Cancelling the collector cancels the walk and the digests, because the per-file digests
     * are structural children of the collecting job.
     */
    fun find(): Flow<DuplicateScanProgress>
}
