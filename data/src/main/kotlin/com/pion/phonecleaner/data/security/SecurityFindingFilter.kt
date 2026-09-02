package com.pion.phonecleaner.data.security

import com.pion.phonecleaner.domain.model.security.RiskLevel
import com.pion.phonecleaner.domain.model.security.ThreatVerdict
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * **What counts as a finding** — the app-side filter of `docs/screens/15-antivirus.md` §0.4.
 *
 * It lives here, in the repository's own package, and **not in either ViewModel**: it is a domain
 * rule, and both screens need the same answer. Kept as a pure function over a list so it is a unit
 * test with no fakes (`LLM.md` §4).
 *
 * Three rules, in this order:
 *
 *  1. keep `score >= RiskLevel.ELEVATED_SCORE` — the competitor's own `>= 6` list filter;
 *  2. de-duplicate rows that carry a package name **by package name** — the vendor can report the
 *     same installed app more than once (split APKs, a second path for the same package), and the
 *     competitor renders both;
 *  3. keep every row that has **no** package name. A loose `.apk` and a test-file hit both have an
 *     empty package name, so collapsing on it would fold unrelated files into one row (§2.3).
 *
 * Order is stable and is the vendor's, so the screen sorts by nothing implicit; the worst-first
 * ordering the result screen renders is the DAO's `ORDER BY score DESC`.
 */
internal fun List<ThreatVerdict>.asFindings(): ImmutableList<ThreatVerdict> {
    val seenPackages = HashSet<String>()
    return filter { it.score >= RiskLevel.ELEVATED_SCORE }
        .filter { verdict -> !verdict.isInstalledApp || seenPackages.add(verdict.packageName) }
        .toImmutableList()
}
