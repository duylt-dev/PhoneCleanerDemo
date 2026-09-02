package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.domain.model.junk.AppRule
import com.pion.phonecleaner.domain.model.junk.SystemCacheRule
import com.pion.phonecleaner.domain.repository.JunkRuleCatalog
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The catalogue implementation that ships today: **it carries no rules at all.**
 *
 * ## PENDING OWNER DECISION (1) — this class is the placeholder, not the answer
 *
 * `docs/screens/12-junk-cleaning.md` §2 records the rule catalogue as an **unsettled fork**, and
 * §8.4 item 1 confirms the owner has not ruled:
 *
 *  * **Branch A — `AssetJunkRuleCatalog`**: ship the competitor's 41 system-cache rules and its
 *    208-app / 224-root table as `app/src/main/assets/junk-rules.json`, parsed against
 *    `schemaVersion`. The largest single code deletion available in the project.
 *  * **Branch B — `KotlinJunkRuleCatalog`**: build our own list and return it from code.
 *
 * The blocking question is not technical. The provenance of the competitor's own rule set is
 * **UNKNOWN** (`r1-01` OQ4), which makes branch A a redistribution question the owner has deferred.
 * **No rule data has therefore been copied into this repository**, and this class exists so that the
 * scanner, the repository and all three screens can be written, compiled and tested against the real
 * seam while that decision is open. Both branches remain exactly one implementation class away: swap
 * the `single<JunkRuleCatalog>` line in `junkDataModule` and nothing else in the cluster changes.
 *
 * ### What this costs today, stated rather than hidden
 *
 * With no rules, the system-cache and app-residual passes evaluate zero candidates and yield no
 * category. The `.apk` pass reads no catalogue and is unaffected, so a scan still returns real
 * results. `JunkScanState.progress` and the review screen behave identically either way — the two
 * empty passes simply finish immediately.
 */
internal class EmptyJunkRuleCatalog : JunkRuleCatalog {

    override suspend fun systemCacheRules(): ImmutableList<SystemCacheRule> = persistentListOf()

    override suspend fun appRules(): ImmutableList<AppRule> = persistentListOf()
}
