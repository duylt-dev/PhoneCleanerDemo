package com.pion.phonecleaner.data.junk

import java.io.File

/** What deleting one junk path actually achieved. */
internal class DeleteTally(val freedBytes: Long, val removed: Boolean)

/**
 * Deletes one junk path — a file or a whole directory — **bounded**, and reports what the filesystem
 * actually returned.
 *
 * The competitor's whole implementation is `FilesKt.deleteRecursively(File(path))` inside
 * `catch (Exception) { printStackTrace() }` returning `0L`, and it re-measures the size with another
 * full recursive walk immediately before each delete (`MenaremovActivity.java:242`, `c0()`).
 * Here the bytes are counted as each file is removed, so the number reported is the number freed —
 * not the number promised.
 *
 * Three bounds `deleteRecursively` has none of: a depth cap, a symlink guard (deleting *through* a
 * link would delete the link's target, which is not what any rule asked for), and a visited set.
 *
 * **Partial deletes count as failures and their bytes are not credited.** That under-reports, which
 * is the deliberate opposite of the competitor's `if (cleanedSize > 0) cleanedSize else totalSize`
 * (`MenaremovActivity.java:325-328`, Delta C3), where a total failure is reported to the user and to
 * the lifetime ledger as a total success. `CleanProgress.Failed` carries no byte count and inventing
 * one on it would be a contract change no source asks for.
 */
internal fun deleteJunkTree(root: File, maxDepth: Int): DeleteTally {
    if (!root.exists()) return DeleteTally(0L, removed = true)
    if (!root.isDirectory || isSymbolicLink(root)) {
        val length = root.length()
        return if (root.delete()) DeleteTally(length, true) else DeleteTally(0L, false)
    }

    var freed = 0L
    val visited = HashSet<String>()
    val directories = ArrayList<File>()
    val pending = ArrayDeque<Pair<File, Int>>()
    pending.addLast(root to 0)

    while (pending.isNotEmpty()) {
        val (directory, depth) = pending.removeLast()
        val canonical = canonicalOrNull(directory) ?: continue
        if (!visited.add(canonical)) continue
        directories += directory
        val children = directory.listFiles() ?: continue
        for (child in children) {
            if (child.isDirectory && !isSymbolicLink(child)) {
                if (depth < maxDepth) pending.addLast(child to depth + 1)
            } else {
                val length = child.length()
                if (child.delete()) freed += length
            }
        }
    }

    // Pre-order push order means the reverse is a valid post-order: every child precedes its parent,
    // so a directory is only attempted once its contents are gone.
    directories.asReversed().forEach { it.delete() }
    return DeleteTally(freed, removed = !root.exists())
}

private fun isSymbolicLink(file: File): Boolean {
    val parentCanonical = file.parentFile?.let(::canonicalOrNull) ?: return false
    val canonical = canonicalOrNull(file) ?: return false
    return canonical != "$parentCanonical/${file.name}"
}

private fun canonicalOrNull(file: File): String? =
    runCatching { file.canonicalPath.trimEnd('/').ifEmpty { "/" } }.getOrNull()
