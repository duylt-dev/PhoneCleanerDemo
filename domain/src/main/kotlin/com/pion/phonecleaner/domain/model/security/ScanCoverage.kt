package com.pion.phonecleaner.domain.model.security

/**
 * What the scan was able to look at.
 *
 * `docs/system-architecture.md` §8.4 makes this mandatory in the default (MediaStore + SAF) branch:
 * **a scan that cannot see everything must say so.** The competitor's file-reputation scan silently
 * degrades to installed packages only and reports nothing about it — the user reads "0 findings" as
 * "nothing here", when half the surfaces were never opened
 * (`docs/screens/15-antivirus.md` §1.5, last-but-two row).
 *
 * UNKNOWN — the shape. `docs/screens/15-antivirus.md` open item 4 and
 * `docs/screens/14-file-tools-and-app-manager.md` open item 2 both record that `ScanCoverage` is an
 * appendix's own name and that **no report defines its members**; the two uses that are written down
 * are the constant `ScanCoverage.Unknown` and the predicate `coverage.isPartial`. Looked for and not
 * found: a member list in `docs/system-architecture.md` §8.4, in either appendix, and in the shared
 * API digest. The three constants below are the only distinction this cluster's own screens can
 * observe — whether the storage surfaces were readable — and nothing finer is invented.
 *
 * The file-tools cluster may define a richer type of the same name in its own package; that is a
 * duplication to resolve when both land, not a reason for either to guess the other's shape.
 */
enum class ScanCoverage {

    /** Nothing has reported yet. Renders as nothing — never as "complete". */
    Unknown,

    /** Installed apps only: the storage surfaces were not readable, and the screen says so. */
    InstalledAppsOnly,

    /** Installed apps plus the storage surfaces the current grants reach. */
    InstalledAppsAndFiles,
    ;

    /** True only when something was demonstrably skipped — [Unknown] is not a claim either way. */
    val isPartial: Boolean get() = this == InstalledAppsOnly
}
