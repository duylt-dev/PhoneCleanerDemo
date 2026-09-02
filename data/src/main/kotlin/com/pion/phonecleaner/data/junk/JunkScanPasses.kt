package com.pion.phonecleaner.data.junk

import com.pion.phonecleaner.domain.model.file.FileKind
import com.pion.phonecleaner.domain.model.junk.AppRule
import com.pion.phonecleaner.domain.model.junk.JunkCategory
import com.pion.phonecleaner.domain.model.junk.JunkCategoryId
import com.pion.phonecleaner.domain.model.junk.JunkContentType
import com.pion.phonecleaner.domain.model.junk.JunkItem
import com.pion.phonecleaner.domain.model.junk.JunkOrigin
import com.pion.phonecleaner.domain.model.junk.ScanProgress
import com.pion.phonecleaner.domain.model.junk.SystemCacheRule
import com.pion.phonecleaner.domain.repository.DirectorySizer
import com.pion.phonecleaner.domain.repository.StorageScanner
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.FlowCollector
import java.io.File
import java.util.Locale

/**
 * The three passes, as functions over a `FlowCollector<ScanProgress>`.
 *
 * They live beside `RuleJunkScanner` rather than inside it so that neither file exceeds the 200-line
 * limit and so that "what a pass does" reads apart from "how the three are sequenced"
 * (`.claude/rules/development-rules.md`).
 *
 * Two invariants hold in all three: `ensureActive()` runs per candidate, so cancelling the collector
 * stops the pass; and the candidate list of the two determinate passes is built **before** the
 * sizing loop, which is what makes `ScanProgress.Candidate.total` a real number
 * (`docs/screens/12-junk-cleaning.md` §1.2).
 */

/** Pass 1 — folders named by a system-cache rule. Determinate: the rule count is the total. */
internal suspend fun FlowCollector<ScanProgress>.systemCachePass(
    rules: List<SystemCacheRule>,
    roots: List<String>,
    sizer: DirectorySizer,
): JunkCategory? {
    emit(ScanProgress.PassStarted(JunkCategoryId.SystemCache))
    val candidates = rules
        .flatMap { rule -> roots.map { root -> rule to resolveUnder(root, rule.path) } }
        .filter { (_, path) -> File(path).isDirectory }
        .distinctBy { (_, path) -> path.lowercase(Locale.ROOT) }
    val items = ArrayList<JunkItem>()
    candidates.forEachIndexed { index, (rule, path) ->
        currentCoroutineContext().ensureActive()
        val size = sizer.sizeOf(JunkWalkBounds.ruleDirectory(path)).getOrNull() ?: 0L
        emit(ScanProgress.Candidate(path, index + 1, candidates.size, size))
        if (size > 0L && items.size < JunkWalkBounds.MAX_ITEMS_PER_CATEGORY) {
            // The label is the RULE fragment, never `File(path).name`: the competitor's row reads
            // `cache`, `temp` or `.thumbnails` and says nothing (Delta R4). UNKNOWN — a localised
            // display name for a system-cache rule has no source in the corpus, so the fragment the
            // rule itself names is used rather than an invented one.
            items += JunkItem(
                path = path,
                label = rule.path,
                sizeBytes = size,
                origin = JunkOrigin.SystemCacheRule(rule.id, rule.contentType),
            )
        }
    }
    return categoryOf(JunkCategoryId.SystemCache, items)
}

/** Pass 2 — leftovers of apps that are no longer installed. Determinate. */
internal suspend fun FlowCollector<ScanProgress>.appResidualPass(
    rules: List<AppRule>,
    roots: List<String>,
    sizer: DirectorySizer,
): JunkCategory? {
    emit(ScanProgress.PassStarted(JunkCategoryId.AppResidual))
    val candidates = residualCandidates(rules, roots)
    val items = ArrayList<JunkItem>()
    candidates.forEachIndexed { index, candidate ->
        currentCoroutineContext().ensureActive()
        val size = sizer.sizeOf(JunkWalkBounds.ruleDirectory(candidate.path)).getOrNull() ?: 0L
        emit(ScanProgress.Candidate(candidate.path, index + 1, candidates.size, size))
        if (size > 0L && items.size < JunkWalkBounds.MAX_ITEMS_PER_CATEGORY) {
            items += JunkItem(candidate.path, candidate.label, size, candidate.origin)
        }
    }
    return categoryOf(JunkCategoryId.AppResidual, items)
}

/**
 * Pass 3 — `.apk` files. **Indeterminate**: it discovers its own candidates, so `total` is
 * [JunkWalkBounds.TOTAL_UNKNOWN] and the screen renders the bar indeterminate rather than inventing
 * a number (Delta S1).
 */
internal suspend fun FlowCollector<ScanProgress>.apkPass(
    roots: List<String>,
    scanner: StorageScanner,
): JunkCategory? {
    emit(ScanProgress.PassStarted(JunkCategoryId.ApkFiles))
    val items = ArrayList<JunkItem>()
    var index = 0
    scanner.walk(JunkWalkBounds.apkWalk(roots)).collect { file ->
        if (file.kind != FileKind.Apk) return@collect
        index++
        emit(ScanProgress.Candidate(file.path, index, JunkWalkBounds.TOTAL_UNKNOWN, file.sizeBytes))
        if (file.sizeBytes > 0L && items.size < JunkWalkBounds.MAX_ITEMS_PER_CATEGORY) {
            items += JunkItem(file.path, file.name, file.sizeBytes, JunkOrigin.ApkFile)
        }
    }
    return categoryOf(JunkCategoryId.ApkFiles, items)
}

private class ResidualCandidate(val path: String, val label: String, val origin: JunkOrigin)

/**
 * Expands app rules into candidate directories.
 *
 * A root carrying [com.pion.phonecleaner.domain.model.junk.AppSubPath]s expands into one candidate
 * per sub-path, which is Delta R5: the data for "delete only sent videos" exists in the catalogue and
 * the competitor cannot read it, because `xc.b` has exactly one getter.
 *
 * A `scoped` root — anything under `Android/data` or `Android/obb` — is **skipped**, because on
 * API 30+ it is unreadable even holding `MANAGE_EXTERNAL_STORAGE` (§2).
 *
 * UNKNOWN — where the skip is *recorded*. §2 requires the result never be presented as "everything
 * scanned", but `ScanProgress` has no arm for a skipped surface and no appendix defines one;
 * `StorageRootProvider.coveredSurfaces()` is the port that exists for saying what was not looked at.
 * Nothing is invented here: the skip happens, and the reporting seam is left where it already is.
 *
 * UNKNOWN — the "app is not installed" filter. `docs/reverse-engineering/12-junk-cleaning.md` §6.3
 * shows the competitor testing `PackageManager.getPackageInfo` and keeping the rule when it FAILS,
 * and §7.3 of the appendix maps that onto `InstalledAppsRepository`. That binding is not declared
 * anywhere yet — `data/di/CoreDataModule.kt` carries it as a commented TODO owned by another cluster
 * — so injecting it here would be a missing binding rather than a filter. With the empty catalogue
 * of PENDING OWNER DECISION (1) there are no app rules to filter, so the two must be settled
 * together: the reported `bindingsForOwner` line names it.
 */
private fun residualCandidates(rules: List<AppRule>, roots: List<String>): List<ResidualCandidate> {
    val seen = HashSet<String>()
    val candidates = ArrayList<ResidualCandidate>()
    for (rule in rules) {
        for (root in rule.roots) {
            if (root.scoped) continue
            val subPaths = root.subPaths.ifEmpty { null }
            for (storageRoot in roots) {
                val base = resolveUnder(storageRoot, root.path)
                if (subPaths == null) {
                    addCandidate(candidates, seen, base, rule, JunkContentType.Unknown)
                } else {
                    subPaths.forEach { sub ->
                        addCandidate(candidates, seen, resolveUnder(base, sub.path), rule, sub.contentType)
                    }
                }
            }
        }
    }
    return candidates
}

private fun addCandidate(
    into: MutableList<ResidualCandidate>,
    seen: MutableSet<String>,
    path: String,
    rule: AppRule,
    contentType: JunkContentType,
) {
    if (!seen.add(path.lowercase(Locale.ROOT))) return
    if (!File(path).isDirectory) return
    into += ResidualCandidate(
        path = path,
        // `AppRule.label` is the competitor's `xc.o.f82763b`: stored 208 times, never read (Delta R4).
        label = rule.label,
        origin = JunkOrigin.UninstalledApp(rule.packageName, rule.label, contentType),
    )
}

private fun categoryOf(id: JunkCategoryId, items: List<JunkItem>): JunkCategory? =
    if (items.isEmpty()) null
    else JunkCategory(id, items.toImmutableList(), items.sumOf { it.sizeBytes })

/** Joins a rule fragment onto a root. `e()` in the competitor strips the separators; so do we. */
private fun resolveUnder(root: String, fragment: String): String =
    File(root.trimEnd('/'), fragment.trim('/')).path
