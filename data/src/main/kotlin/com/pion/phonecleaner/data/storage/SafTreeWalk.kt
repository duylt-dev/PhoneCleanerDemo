package com.pion.phonecleaner.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.pion.phonecleaner.domain.model.file.FileOrigin
import com.pion.phonecleaner.domain.model.file.ScannedFile
import com.pion.phonecleaner.domain.model.file.WalkConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.time.TimeSource

/**
 * The traversal of a user-granted SAF tree — strategy 4 of `docs/system-architecture.md` §8.3, and
 * half of what makes the default branch a real branch rather than a placeholder.
 *
 * A tree the user pointed at with `ACTION_OPEN_DOCUMENT_TREE` needs **no storage permission at all**,
 * and it is the only way the default branch reaches a non-media directory — the legacy `/WhatsApp`
 * root, a downloads folder, an app-data folder the user chose. Without it, dropping
 * `MANAGE_EXTERNAL_STORAGE` would mean the scanner silently sees nothing outside MediaStore, which is
 * the competitor's exact defect: without the storage grant its file scan *"silently degrades to
 * installed packages only and reports nothing about it"* (§8.2, last row).
 *
 * Bounded exactly like [walkFilesBounded]: `maxDepth`, a visited set, a monotonic deadline,
 * `excludedRoots` and one `ensureActive()` per directory. No symlink guard — a document tree has no
 * links to loop through; the visited set covers a provider that returns a cycle anyway.
 *
 * It is slower than a filesystem walk by a wide margin: every level is a `ContentResolver` query
 * across a Binder. That is the price of a grant the user actually controls, and it is why a SAF tree
 * is scanned only when the user has added one.
 */
internal suspend fun walkSafTreeBounded(
    context: Context,
    treeUris: List<String>,
    config: WalkConfig,
    onFile: suspend (ScannedFile) -> Unit,
) {
    if (treeUris.isEmpty()) return
    val deadline = config.timeLimit?.let { TimeSource.Monotonic.markNow() + it }
    val visited = HashSet<String>()
    val pending = ArrayDeque<Pair<DocumentFile, Int>>()
    for (uri in treeUris) {
        val root = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uri)) }.getOrNull()
        if (root != null && root.isDirectory) pending.addLast(root to 0)
    }

    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        if (deadline != null && deadline.hasPassedNow()) return
        val (directory, depth) = pending.removeLast()
        val identity = directory.uri.toString()
        if (!visited.add(identity)) continue
        if (config.excludedRoots.any { identity.startsWith(it) }) continue

        val children = runCatching { directory.listFiles() }.getOrNull() ?: continue
        for (child in children) {
            if (child.isDirectory) {
                if (depth < config.maxDepth) pending.addLast(child to depth + 1)
            } else {
                onFile(child.toScannedFile())
            }
        }
    }
}

/**
 * A document has no filesystem path, so [ScannedFile.path] carries its document URI.
 *
 * **Why the origin is [FileOrigin.MediaStoreEntry] and not [FileOrigin.PlainFile].** The domain's
 * `FileOrigin` has two arms: a path you may `File.delete()`, and a `content://` URI resolved **at
 * scan time**. A SAF document is the second — it has no deletable path — so it takes that arm, and
 * `DefaultFileDeleter` dispatches on the URI's authority: `media` goes to
 * `MediaStore.createDeleteRequest`, anything else to `DocumentsContract.deleteDocument`. Resolving
 * the URI at scan time is the one delete rule two clusters found independently: the competitor
 * re-resolves a row **by `_display_name`** at delete time even though the scan already selected
 * `_id`, so a delete can hit the wrong row (`java/nd/g.java:75`; §4.5).
 *
 * A third arm (`DocumentTreeEntry`) would say this in the type instead of in a comment, but
 * `FileOrigin` is a `:domain` file and not this agent's to change.
 */
private fun DocumentFile.toScannedFile(): ScannedFile {
    val displayName = name ?: uri.lastPathSegment.orEmpty()
    val mime = type ?: FileTypeResolver.mimeTypeOf(displayName)
    return ScannedFile(
        id = uri.toString(),
        path = uri.toString(),
        name = displayName,
        sizeBytes = length(),
        kind = FileTypeResolver.of(displayName, mime),
        origin = FileOrigin.MediaStoreEntry(uri.toString()),
        mimeType = mime,
        lastModifiedAtMillis = lastModified(),
    )
}
