package com.pion.phonecleaner.domain.model.junk

import kotlinx.serialization.Serializable

/**
 * The one argument of the `JunkScan` route (`docs/screens/12-junk-cleaning.md` §8.1).
 *
 * It lives in `:domain` for the same reason [com.pion.phonecleaner.domain.model.settings.LegalDocument]
 * does: `:app` declares the route and `:feature:junk` reads the argument, and those two modules may
 * not see each other's presentation types (`LLM.md` §2, §7.2).
 *
 * [Express] is chapter 14's "junk clean express" entry, which `docs/screens/12-junk-cleaning.md`
 * §8.1 confirms is **this cluster's engine, not a second one**.
 *
 * **UNKNOWN — Express has no screen design.** §8.4 item 4 states it plainly: no report writes the
 * express route's own state, so its skip-the-review behaviour is described and not specified.
 * Nothing in this cluster branches on it yet; the mode is carried so the decision has somewhere to
 * land rather than being closed off by a missing field.
 */
@Serializable
enum class JunkScanMode {
    Review,
    Express,
}
