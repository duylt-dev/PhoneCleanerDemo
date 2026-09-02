package com.pion.phonecleaner.data.storage

import com.pion.phonecleaner.domain.model.file.WalkConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import kotlin.time.TimeSource

/**
 * The ONE filesystem traversal in the app. `DefaultStorageScanner` and `BoundedDirectorySizer` both
 * call it; nothing re-implements a walk (`docs/system-architecture.md` §4.5).
 *
 * It exists because of `xc.c.a` (`java/xc/c.java:15-35`) — an unbounded recursive sum with no depth
 * cap, no visited-inode set, no time cap and no symlink guard — and `xc.j.a()`, which fans out one
 * `async` per sub-directory, so a 200 000-directory device gets 200 000 coroutines
 * (`docs/system-architecture.md` §7.4). Every bound §4.5's table names is applied here, once:
 *
 * | Bound | How |
 * |---|---|
 * | `maxDepth` | required on [WalkConfig]; a directory at the limit is listed but not descended into |
 * | visited set | canonical paths, which is what makes a symlink *loop* terminate |
 * | parallelism | none. This is one coroutine walking one stack — a fan-out of zero is inside the limit of four, and it needs no dispatcher of its own |
 * | cancellation | `ensureActive()` once per directory |
 * | `excludedRoots` | a prefix test per directory, before it is listed |
 * | `timeLimit` | a monotonic deadline; a wall-clock change cannot extend or shorten it |
 *
 * Ending on a time limit or a depth limit is **not** an error: the caller is told what was covered
 * through `StorageRootProvider.coveredSurfaces()`, and a partial answer is never presented as
 * complete (§8.4, consequence 1).
 */
internal suspend fun walkFilesBounded(
    roots: List<String>,
    config: WalkConfig,
    onFile: suspend (File) -> Unit,
) {
    if (roots.isEmpty()) return
    val deadline = config.timeLimit?.let { TimeSource.Monotonic.markNow() + it }
    val excluded = config.excludedRoots.map(::normalise)
    val visited = HashSet<String>()
    val pending = ArrayDeque<Pair<File, Int>>()
    roots.forEach { pending.addLast(File(it) to 0) }

    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        if (deadline != null && deadline.hasPassedNow()) return
        val (directory, depth) = pending.removeLast()
        val canonical = canonicalOrNull(directory) ?: continue
        if (!visited.add(canonical)) continue
        if (excluded.any { canonical == it || canonical.startsWith("$it/") }) continue

        val children = directory.listFiles() ?: continue
        for (child in children) {
            if (child.isDirectory) {
                if (depth >= config.maxDepth) continue
                if (!config.followSymlinks && isLink(child)) continue
                pending.addLast(child to depth + 1)
            } else if (child.isFile) {
                if (!config.followSymlinks && isLink(child)) continue
                onFile(child)
            }
        }
    }
}

/**
 * True when [file]'s own last path segment is a link. Compared against the PARENT's canonical path,
 * not the file's own absolute path: `/sdcard` is itself a link to `/storage/emulated/0` on most
 * devices, so testing `canonicalPath != absolutePath` would reject every root under it.
 */
private fun isLink(file: File): Boolean {
    val parentCanonical = file.parentFile?.let(::canonicalOrNull) ?: return false
    val canonical = canonicalOrNull(file) ?: return false
    return canonical != "$parentCanonical/${file.name}"
}

private fun canonicalOrNull(file: File): String? =
    runCatching { normalise(file.canonicalPath) }.getOrNull()

private fun normalise(path: String): String = path.trimEnd('/').ifEmpty { "/" }
