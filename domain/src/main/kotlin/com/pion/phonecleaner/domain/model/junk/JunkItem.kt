package com.pion.phonecleaner.domain.model.junk

/**
 * One path a scan found.
 *
 * **[sizeBytes] is the original size and is never zeroed.** The competitor encodes "deselected" by
 * writing `size = 0` into the shared node (`yc/a.java:175-185`), which is why it needs two snapshot
 * maps to remember what the size used to be — and the nodes it mutates are the same instances still
 * held by a `volatile` static (`docs/screens/12-junk-cleaning.md` §4.4 Delta R1). Selection here is
 * a `Set<String>` of [path] on the review screen's state (`LLM.md` §8).
 *
 * [path] is the identity: it is the `LazyColumn` key, the selection element and what the deleter
 * receives.
 */
data class JunkItem(
    /** Absolute path, or a SAF document URI where the walk came from a granted tree. The IDENTITY. */
    val path: String,
    /** What the user reads. Built by the mapper from [origin], never by a composable. */
    val label: String,
    /** BYTES. Never kilobytes, never a formatted string. */
    val sizeBytes: Long,
    val origin: JunkOrigin,
)
