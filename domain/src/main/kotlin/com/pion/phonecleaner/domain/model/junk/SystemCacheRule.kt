package com.pion.phonecleaner.domain.model.junk

/**
 * One system-cache rule: a folder fragment, relative to a storage root, that is junk when it exists.
 *
 * The shape is the one `docs/screens/12-junk-cleaning.md` §2 sketches for `junk-rules.json`
 * (`{ "id", "path", "contentType" }`), expressed as a type so that **both** branches of that
 * unsettled fork satisfy the same [com.pion.phonecleaner.domain.repository.JunkRuleCatalog].
 *
 * PENDING OWNER DECISION (1) — no rule DATA is carried in this repository. See `JunkRuleCatalog`.
 */
data class SystemCacheRule(
    /** Stable id, carried onto [JunkOrigin.SystemCacheRule] so a row traces back to its rule. */
    val id: String,
    /** Path fragment relative to a storage root. No leading or trailing separator. */
    val path: String,
    val contentType: JunkContentType,
)
