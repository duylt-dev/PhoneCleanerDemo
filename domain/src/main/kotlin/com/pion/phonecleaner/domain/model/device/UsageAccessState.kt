package com.pion.phonecleaner.domain.model.device

/**
 * Whether this app holds the `PACKAGE_USAGE_STATS` special access.
 *
 * ### PENDING OWNER DECISION — `docs/screens/18-device-battery-and-apps.md` §0.1, tracked as
 * `docs/system-architecture.md` §10.1 **P1**. It is **not settled**, and nothing here settles it.
 *
 * The two live outcomes are: ship the running-apps feature with a real grant (the list becomes
 * `UsageStatsManager.queryEvents` over a recent window, i.e. apps genuinely foregrounded), or drop
 * the screen. This enum is the seam between them: the ungranted path is a rendered state with a
 * rationale and a "grant" action, so choosing either outcome is one edit rather than a rewrite.
 *
 * **The permission is deliberately NOT declared in any manifest** while the decision is open.
 * `Settings.ACTION_USAGE_ACCESS_SETTINGS` can be launched without declaring it; only *reading* the
 * stats needs the declaration, and no code in this port reads them yet.
 */
enum class UsageAccessState {
    /** Not yet checked. The first frame, and the only value that renders neither branch. */
    Unknown,
    Granted,
    Denied,
}
