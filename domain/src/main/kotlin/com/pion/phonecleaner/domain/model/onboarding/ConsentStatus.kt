package com.pion.phonecleaner.domain.model.onboarding

import kotlinx.collections.immutable.ImmutableSet

/**
 * What the privacy-consent round trip answered.
 *
 * Ports `md.l3` (185 L), whose whole return value the splash **throws away**: `f0` never reads its
 * `returnCode` parameter (`CucurtagActivity.java:274`), so a user who refuses every purpose reaches
 * home on exactly the same path as one who accepts
 * (`docs/reverse-engineering/10-splash-and-onboarding.md:288`).
 */
data class ConsentStatus(
    val outcome: ConsentOutcome,
    /**
     * Whether the consent state permits requesting ads at all. Carried to the boundary that asks;
     * nothing in a ViewModel reads it to decide navigation.
     */
    val canRequestAds: Boolean,
    /**
     * The whole purpose bitstring, not one bit of it. `md.l3.h()` reads **only the first character**
     * of `IABTCF_PurposeConsents` — purpose 1 — and purposes 2-10 are never inspected
     * (`docs/reverse-engineering/10-splash-and-onboarding.md:610`). Exposing the set lets whoever owns
     * the ad boundary decide what it needs, instead of this layer deciding for it.
     */
    val purposeConsents: ImmutableSet<TcfPurpose>,
)

/**
 * The four outcomes `md.l3` can actually produce, collapsed from its `1`/`0` return codes
 * (`docs/reverse-engineering/10-splash-and-onboarding.md:603-606`). The competitor's codes are
 * themselves XOR-decoded single bytes rather than literals; ours are named.
 *
 * [Unavailable] covers both of its "callback value 1" rows — form not available, and
 * `requestConsentInfoUpdate` failed — because neither is a user decision and the splash treats them
 * identically: the gate settles and the flow continues.
 */
enum class ConsentOutcome {
    /** The form was shown and dismissed with consent granted. */
    Granted,
    /** The form was shown and dismissed without consent. */
    Refused,
    /** No form was required in this jurisdiction, or none was available. */
    NotRequired,
    /** The round trip could not complete. The gate still settles; nothing is blocked on it. */
    Unavailable,
}

/**
 * One IAB TCF purpose, by its published number.
 *
 * A number and not an enum of ten names **on purpose**: the corpus verifies exactly one purpose —
 * `md.l3.h()` reads the first character of `IABTCF_PurposeConsents`, "Store and/or access information
 * on a device", i.e. purpose 1 (`docs/reverse-engineering/10-splash-and-onboarding.md:610`). Writing
 * nine more constant names here would be nine names nothing in this project has checked.
 */
@JvmInline
value class TcfPurpose(val id: Int) {
    companion object {
        /** Purpose 1 — the only one the competitor inspects, and the only one named here. */
        val StoreAndAccessInformation = TcfPurpose(1)
    }
}
