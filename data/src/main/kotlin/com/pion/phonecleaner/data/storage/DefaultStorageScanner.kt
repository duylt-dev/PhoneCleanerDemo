package com.pion.phonecleaner.data.storage

import android.content.Context
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.data.trash.TrashRoots
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * One of the three primitives every feature engine composes (`docs/system-architecture.md` §4.5).
 * `JunkScanner`, `DuplicateFinder`, `SimilarPhotoScanner` and `WhatsAppScanner` call this; **none of
 * them re-implements a walk**. The competitor's five engines share no code, and five of its nine
 * scan screens re-declare the same six Activity fields.
 *
 * It replaces `rd.a` (all three of its walk modes) and `mc.b.a()` (the static exclusion list), and it
 * is where every bound of §4.5's table is applied — see [walkFilesBounded].
 *
 * **A screen never names this type.** It names a feature engine, and the engine names this. That is
 * what lets the storage branch change in `storageDataModule` alone (§8.4).
 */
internal class DefaultStorageScanner(
    private val context: Context,
    private val dispatchers: DispatcherProvider,
    /**
     * Required, not nullable: a null here makes the exclusion below silently inert, which is the one
     * failure mode this parameter exists to prevent (plan 260908-0801 phase 03). `storageDataModule`
     * is the only construction site and passes it.
     */
    private val trashRoots: TrashRoots,
) : StorageScanner {

    /**
     * Cold: nothing is read until someone collects, and cancelling the collector cancels the walk at
     * the next directory. The competitor's engine has no cancellation at all.
     *
     * The roots come from `StorageRootProvider`, so this handles both shapes it can return: a
     * filesystem path, and a `content://` tree the user granted through SAF. A path can never start
     * with `content://`, so the split needs no extra field on [WalkConfig].
     *
     * `flowOn(dispatchers.io)` — **the dispatcher choice is made inside the repository, not by the
     * caller** (§7.4). `cd.d.d`'s caller picks the dispatcher, so the same repository behaves
     * differently on each screen.
     */
    override fun walk(config: WalkConfig): Flow<ScannedFile> = flow {
        // The bin sits on a volume root, so an all-files walk would list every trashed file straight
        // back into big files, duplicates and the junk rules — and the junk cleaner would then delete
        // what the user can still restore. Unioned here, once, so every caller inherits it.
        val guarded = config.copy(
            excludedRoots = (config.excludedRoots + trashRoots.allRoots()).toImmutableList(),
        )
        val (trees, paths) = guarded.roots.partition { it.startsWith(TREE_URI_PREFIX) }
        walkFilesBounded(paths, guarded) { emit(it.toScannedFile()) }
        walkSafTreeBounded(context, trees, guarded) { emit(it) }
    }.flowOn(dispatchers.io)

    private companion object {
        const val TREE_URI_PREFIX = "content://"
    }
}

/**
 * [FileOrigin.PlainFile], because a path we walked to is a path we may `File.delete()`. A row that
 * came out of MediaStore or a SAF tree carries its URI instead, and `DefaultFileDeleter` branches on
 * exactly that (§4.5).
 */
private fun File.toScannedFile(): ScannedFile {
    val mime = FileTypeResolver.mimeTypeOf(name)
    return ScannedFile(
        id = absolutePath,
        path = absolutePath,
        name = name,
        sizeBytes = length(),
        kind = FileTypeResolver.of(name, mime),
        origin = FileOrigin.PlainFile,
        mimeType = mime,
        lastModifiedAtMillis = lastModified(),
    )
}
