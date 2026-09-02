package com.pion.phonecleaner.domain.model.security

/**
 * Whether the user has answered the data-collection disclosure — and, if so, how.
 *
 * DELIBERATE DEVIATION from `docs/screens/15-antivirus.md` §0.4, which types this as
 * `hasConsent(): Boolean`. **§0.5 of the same appendix requires two things a Boolean cannot express**
 * and gives the reason for each:
 *
 *  1. *"a rejection is recorded, so 'no' is remembered rather than re-asked on every entry"* — with a
 *     Boolean, [Rejected] and [Unanswered] are the same value, and the non-cancellable dialog
 *     reappears on every visit. That is the competitor's behaviour, which stores a single string and
 *     only ever writes it on acceptance.
 *  2. *"the consent text enumerates exactly what leaves the device, so when that list changes the
 *     consent has to be re-asked"* — the store compares the recorded version with the version of the
 *     text it is showing and reports [Unanswered] when the disclosure has moved on.
 *
 * The two DataStore keys of §0.5 are exactly what this enum is read from.
 */
enum class ScanConsentState {
    /** Never answered, or answered against a disclosure that has since changed. Ask. */
    Unanswered,

    /** Answered "no". Remembered: the screen explains, and does not re-open the dialog. */
    Rejected,

    /** Answered "yes", against the disclosure this build ships. */
    Granted,
}
