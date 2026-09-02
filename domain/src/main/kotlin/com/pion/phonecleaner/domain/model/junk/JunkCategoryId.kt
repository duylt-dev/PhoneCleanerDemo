package com.pion.phonecleaner.domain.model.junk

/**
 * The three passes a junk scan makes, and the three sections the review screen renders.
 *
 * These are the competitor's three `xc.x` category constants — `system_cache`, `app_residual`,
 * `apk_files` (`docs/reverse-engineering/12-junk-cleaning.md` §6) — as an enum rather than as the
 * English string literals it passes to its listener. A screen picks the localised label from the
 * enum; the competitor's listener receives `"System Cache"` and has nothing else to render, which
 * is why its section titles are untranslated in all 17 shipped locales.
 *
 * A fourth competitor constant, `empty_files`, is declared and referenced nowhere, and its
 * collected list has no consumer (§12 finding 23). It is not carried.
 */
enum class JunkCategoryId {
    SystemCache,
    AppResidual,
    ApkFiles,
}
