package com.pion.phonecleaner.domain.model.cleanup

import kotlinx.datetime.LocalDate

/**
 * What one calendar day's cleaning freed.
 *
 * UNKNOWN — the shape is not settled anywhere. `LLM.md` §3.3 names the type in
 * `:domain/model/cleanup/`; `deobfuscation-reference.md:448` maps the competitor's `be.i`
 * (`todayTime: String`, `saveScanMiaoBindiTime: long`, `saveCleanSize: long`, `anzAPPName: String`)
 * onto it; and `docs/screens/21-shared-models-and-ui.md:77` says `be.i` is instead folded into
 * `CleanStatsRepository`'s DataStore record with "the bean's live field is the only one that
 * survives" — without saying which field that is. Those two readings are not reconcilable from the
 * corpus, so only the two facts both agree on are carried here: a day, and bytes freed on it.
 * `saveScanMiaoBindiTime` (the "scanned today" flag) belongs to `CleanStatsRepository` in
 * `backgroundModule`, not here (`docs/screens/11-home.md:308`).
 *
 * A `LocalDate`, not a `String` and not a millis: the competitor's day boundary is a static **GMT**
 * `SimpleDateFormat` (`md.h4`), which is the wrong day for most of the world
 * (`docs/system-architecture.md` §5.10). `kotlinx.datetime.LocalDate` is declared stable in
 * `compose-stability.conf`.
 */
data class DailySavings(
    val day: LocalDate,
    val freedBytes: Long,
)
