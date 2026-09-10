package com.pion.phonecleaner.domain.model.junk

/**
 * Why a path is on the list. It is what makes a row legible, and it is what a delete strategy
 * reads.
 *
 * The competitor labels every row `File(path).name` with the path as a fallback
 * (`yc/a.java:147-150`), so the user reads `cache`, `temp`, `external-sd` and `.goproduct` and can
 * tell neither what any of them is nor which app it belonged to
 * (`docs/screens/12-junk-cleaning.md` §4.4 Delta R4).
 *
 * No `@Immutable` annotation: `:domain` is compiled without the Compose plugin (`LLM.md` §2), and
 * `com.pion.phonecleaner.domain.model.*` is already declared stable in `compose-stability.conf`
 * (`LLM.md` §8). The promise the annotation would make still binds — every property is a `val` of a
 * stable type and no collection here is ever mutated in place.
 */
sealed interface JunkOrigin {

    /**
     * A folder named by a system-cache rule. [ruleId] is the catalogue's own id, so a row can be
     * traced back to the rule that produced it without re-matching the path.
     */
    data class SystemCacheRule(
        val ruleId: String,
        val contentType: JunkContentType,
    ) : JunkOrigin

    /**
     * Leftovers of an app that is **not installed** — which is what the competitor's app-residual
     * rule actually tests (`docs/reverse-engineering/12-junk-cleaning.md` §6.3, finding 2).
     *
     * [appLabel] is the competitor's `xc.o.f82763b`: stored in 208 places and never read. We read
     * it. It is the only string that turns `AlarmClockXtreme` into a name a user recognises.
     */
    data class UninstalledApp(
        val packageName: String,
        val appLabel: String,
        val contentType: JunkContentType,
    ) : JunkOrigin

    /** A `.apk` file found by the walk. The file's own name is already a legible label. */
    data object ApkFile : JunkOrigin

    /** A loose temporary or log file found by the walk. */
    data object TemporaryFile : JunkOrigin
}
