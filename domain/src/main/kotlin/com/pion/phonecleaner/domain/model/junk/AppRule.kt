package com.pion.phonecleaner.domain.model.junk

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/**
 * One app's leftovers: which package, what to call it, and which directories it leaves behind.
 *
 * The shape is `docs/screens/12-junk-cleaning.md` §2's `junk-rules.json` sketch. Three of its fields
 * exist here precisely because the competitor stores them and never reads them (§2's field table):
 * [label] (`xc.o.f82763b`, written in 208 places, read nowhere — it is what makes a row legible),
 * [aliases] (`xc.o.j()`, 15 call sites, no getter — an app that renamed its package still matches),
 * and [AppRuleRoot.subPaths] (336 sites, no getter — the difference between one 1.2 GB row labelled
 * `WhatsApp` and a browsable per-content-type list).
 *
 * [AppRuleRoot] and [AppSubPath] share this file because neither is meaningful alone and neither is
 * referenced without the other — the same shape as `DeleteOutcome.kt` and its `PendingIntentToken`.
 *
 * PENDING OWNER DECISION (1) — no rule DATA is carried in this repository. See `JunkRuleCatalog`.
 */
data class AppRule(
    /** The package that owned these directories. The rule fires when it is **not** installed. */
    val packageName: String,
    /** The app's own display name, for [JunkOrigin.UninstalledApp.appLabel]. */
    val label: String,
    /** Other package names the same app has shipped under. Matched alongside [packageName]. */
    val aliases: ImmutableSet<String>,
    val roots: ImmutableList<AppRuleRoot>,
)

/**
 * One directory the app leaves behind, relative to a storage root.
 *
 * [scoped] marks a root under `Android/data` or `Android/obb`. On API 30+ those are unreadable even
 * holding `MANAGE_EXTERNAL_STORAGE`, so a scoped root is **skipped, deliberately and knowably** —
 * never silently filtered. The competitor filters the whole `/Android` subtree twice and says
 * nothing, which is why 27 of its 224 roots are dead on arrival
 * (`docs/screens/12-junk-cleaning.md` §2; `docs/reverse-engineering/12-junk-cleaning.md` §12
 * finding 5).
 */
data class AppRuleRoot(
    val path: String,
    val scoped: Boolean = false,
    val subPaths: ImmutableList<AppSubPath>,
)

/**
 * One content-typed directory inside an [AppRuleRoot] — the data behind "delete only sent videos",
 * which the competitor has and cannot read.
 *
 * DELIBERATE OMISSION — §2's JSON sketch also carries a `"direction": "sent"` field beside
 * `contentType`. Nothing in the appendix reads it, and every distinction it could draw is already
 * drawn by [JunkContentType] (`SentImages` versus `Images`). It is not modelled rather than modelled
 * and left unused, which is the defect the three fields above are here to fix.
 */
data class AppSubPath(
    val path: String,
    val contentType: JunkContentType,
)
