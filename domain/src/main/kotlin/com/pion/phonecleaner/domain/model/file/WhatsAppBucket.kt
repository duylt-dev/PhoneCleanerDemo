package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList

/**
 * The six groups the WhatsApp cleaner reports, in the order it renders them
 * (`docs/screens/14-file-tools-and-app-manager.md` §6.1).
 *
 * An enum, not the competitor's `int` bucket index: its own array has `/WhatsApp/.trash` listed twice
 * in one bucket, and an index carries no meaning a reader can check.
 */
enum class WhatsAppBucketId {
    Junk,
    Video,
    Image,
    Voice,
    Audio,
    Document,
}

/**
 * One group, with the files that make it up.
 *
 * [files] is carried because the delete needs it and because the drill-down sheet renders it — the
 * competitor holds six totals and nothing else, so there is no way to see or spare a single file
 * although its own scan already walked them (§6.5).
 *
 * It is a `:domain` model rather than a screen type because `WhatsAppScanner` — a `:domain`
 * repository interface — emits it, and `:domain` may not see a feature module (`LLM.md` §2).
 */
data class WhatsAppBucket(
    val id: WhatsAppBucketId,
    val files: ImmutableList<ScannedFile>,
) {
    val totalBytes: Long get() = files.sumOf { it.sizeBytes }
    val fileCount: Int get() = files.size
    val isEmpty: Boolean get() = files.isEmpty()
}
