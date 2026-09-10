package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.junk.AppRule
import com.pion.phonecleaner.domain.model.junk.SystemCacheRule
import kotlinx.collections.immutable.ImmutableList

/**
 * Where the scanner's rules come from.
 *
 * ## PENDING OWNER DECISION (1) is CLOSED — 2026-09-06, branch 2
 *
 * `docs/screens/12-junk-cleaning.md` §2 offered two branches:
 *
 *  1. ship the competitor's 41 system-cache rules and 208-app table as
 *     `app/src/main/assets/junk-rules.json`, read by an `AssetJunkRuleCatalog`; or
 *  2. build our own list, returned from code by a `KotlinJunkRuleCatalog`.
 *
 * **The owner chose 2.** Branch 1 was the largest single code deletion available in the project —
 * 2 395 lines of Java that are really a data file — but it turned on **a redistribution question
 * about a third-party rule set whose own provenance is UNKNOWN** (`r1-01` OQ4), and that question
 * has no answer available to us. **No rule data from the competitor is carried in this repository,
 * and none may be added**; `decompiler/` is read-only and is a hazard inventory, not a source.
 *
 * The interface stays, and not out of habit: it is what makes the choice reversible and what lets a
 * test hand the scanner a two-rule catalogue instead of the shipping one. The implementation is
 * `KotlinJunkRuleCatalog` in `:data/junk`, whose KDoc states what our own list costs against the
 * competitor's totals.
 *
 * DECLARED IN `junkDataModule` (`docs/screens/12-junk-cleaning.md` §7.1).
 */
interface JunkRuleCatalog {

    /** Folder fragments that are junk when they exist under a storage root. */
    suspend fun systemCacheRules(): ImmutableList<SystemCacheRule>

    /** Per-app leftovers. A rule fires when its app is **not** installed. */
    suspend fun appRules(): ImmutableList<AppRule>
}
