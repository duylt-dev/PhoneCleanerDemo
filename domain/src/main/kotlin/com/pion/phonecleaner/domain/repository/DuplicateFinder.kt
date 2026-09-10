package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.file.DuplicateScanProgress
import kotlinx.coroutines.flow.Flow

/**
 * The duplicate engine. It **composes** [StorageScanner], [MediaStoreRepository], [FileDigest] and
 * [StorageRootProvider] and re-implements none of them (`docs/system-architecture.md` §4.5): five
 * competitor scanner families that share no code collapse into those primitives, and an engine that
 * walks its own tree is how the sixth one came to exist.
 *
 * DECLARED IN `filesDataModule` as `Md5DuplicateFinder`.
 *
 * The pipeline it owns, in one place rather than the competitor's five stages spread across an
 * Activity and a ViewModel (`docs/screens/14-file-tools-and-app-manager.md` §2.2):
 *
 *  1. **collect** every row the current grant reaches. With all-files access that is a walk of every
 *     mounted volume, so **every extension** is in the corpus — a duplicate `.pdf`, `.apk` or `.zip`
 *     is a duplicate. Without it, the three media collections, which is all `MediaStore` can see;
 *  2. dedupe by `ScannedFile.id`, the one key that is unique in both branches;
 *  3. group by **exact byte size**, keeping groups of more than one. This is the cheap pre-filter the
 *     competitor does not have, and without it every same-size-different-content file is read end to
 *     end;
 *  4. digest those candidates — a head digest first, a full digest only on a head collision;
 *  5. group by digest, newest member first.
 *
 * **The branch is chosen here and nowhere else.** No screen, ViewModel or use case learns which one
 * ran (`docs/system-architecture.md` §8.4); what a partial run covered is reported through
 * [StorageRootProvider.coveredSurfaces] and through `DuplicateScanProgress.Finished.truncated`.
 */
interface DuplicateFinder {

    /**
     * Cold. Cancelling the collector cancels the walk and the digests, because the per-file digests
     * are structural children of the collecting job.
     */
    fun find(): Flow<DuplicateScanProgress>
}
