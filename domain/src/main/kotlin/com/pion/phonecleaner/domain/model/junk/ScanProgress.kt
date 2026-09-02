package com.pion.phonecleaner.domain.model.junk

import kotlinx.collections.immutable.ImmutableList

/**
 * What a scan reports while it runs. One cold `Flow` replaces the whole of the competitor's `xc.x`
 * (296 L), its three-method callback interface and the 46-line listener on `TaribrActivity`
 * (`docs/screens/12-junk-cleaning.md` §1.2). The mapping is one-to-one:
 *
 * | Competitor | Ours |
 * |---|---|
 * | `listener.a(category: String)` — the literals `"System Cache"` / `"App Residuals"` / `"APK Files"` | [PassStarted], an enum, so the screen picks the localised string |
 * | `listener.b(path, index, total, size)` | [Candidate] — identical payload |
 * | `listener.c(category, result)` — **body empty in the only implementation** | [PassFinished] — actually consumed |
 * | everything at the end | [Finished] — the terminal emission |
 */
sealed interface ScanProgress {

    data class PassStarted(val category: JunkCategoryId) : ScanProgress

    /**
     * One candidate evaluated.
     *
     * [total] is `-1` while the pass genuinely cannot know it — the `.apk` tree walk, which
     * discovers its own candidates as it goes. The screen renders that indeterminate rather than
     * inventing a number. The competitor's bar instead pins its target at 0 by integer division and
     * animates on a timer, so a 40-second scan and a 2-second scan look identical
     * (`wc/e.java:173`, Delta S1).
     */
    data class Candidate(
        val path: String,
        val index: Int,
        val total: Int,
        val sizeBytes: Long,
    ) : ScanProgress

    /** `null` means the pass found nothing worth a section. */
    data class PassFinished(val category: JunkCategory?) : ScanProgress

    /**
     * The terminal emission, and the authoritative one: it carries the complete category list, so a
     * conflated [PassFinished] costs an early reveal and never data
     * (`docs/screens/12-junk-cleaning.md` §3.2, the `conflate()` row).
     */
    data class Finished(
        val categories: ImmutableList<JunkCategory>,
        val totalBytes: Long,
    ) : ScanProgress
}
