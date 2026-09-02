package com.pion.phonecleaner.domain.repository

import com.pion.phonecleaner.domain.model.junk.AppRule
import com.pion.phonecleaner.domain.model.junk.SystemCacheRule
import kotlinx.collections.immutable.ImmutableList

/**
 * Where the scanner's rules come from — **an interface precisely because the source is undecided**.
 *
 * ## PENDING OWNER DECISION (1): the rule catalogue is an unsettled fork
 *
 * `docs/screens/12-junk-cleaning.md` §2 presents two branches and rules neither:
 *
 *  1. ship the competitor's 41 system-cache rules and 208-app table as
 *     `app/src/main/assets/junk-rules.json`, read by an `AssetJunkRuleCatalog`; or
 *  2. build our own list, returned from code by a `KotlinJunkRuleCatalog`.
 *
 * It is the largest single code deletion available in the project — 2 395 lines of Java that are
 * really a data file — set against a config-delivery dependency the app does not otherwise have and
 * **a redistribution question about a third-party rule set whose own provenance is UNKNOWN**
 * (`r1-01` OQ4). The owner has not ruled, and §8.4 item 1 records that nothing downstream depends on
 * the outcome: this interface is the seam, and both branches are one implementation class away.
 *
 * **No rule data from the competitor is carried in this repository.** The only implementation
 * shipped today is `EmptyJunkRuleCatalog` in `:data/junk`, which returns nothing. Both passes that
 * read this catalogue therefore find nothing today, and the `.apk` pass — which reads no rules at
 * all — is unaffected. Nothing here decides the fork, and nothing here is shaped so that only one
 * branch would fit.
 *
 * DECLARED IN `junkDataModule` (`docs/screens/12-junk-cleaning.md` §7.1).
 */
interface JunkRuleCatalog {

    /** Folder fragments that are junk when they exist under a storage root. */
    suspend fun systemCacheRules(): ImmutableList<SystemCacheRule>

    /** Per-app leftovers. A rule fires when its app is **not** installed. */
    suspend fun appRules(): ImmutableList<AppRule>
}
