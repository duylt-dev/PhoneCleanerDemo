package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

/**
 * The key table for the cloud-scan data-collection consent
 * (`docs/screens/15-antivirus.md` §0.5). Keys only — the reading and the writing live in
 * `data/security/DataStoreConsentStore.kt`, so a key table never grows a behaviour
 * (`LLM.md` §4, `AppDataStore.kt` rule 2).
 *
 * **Two keys, not the competitor's one string**, and each has a reason:
 *
 *  - [SCAN_DATA_CONSENT] records the *answer*, so a rejection is remembered. The competitor writes
 *    its flag only on acceptance, so "no" is indistinguishable from "not asked" and the
 *    non-cancellable dialog returns on every entry.
 *  - [SCAN_CONSENT_VERSION] records *which disclosure* was answered. The consent text enumerates
 *    exactly what leaves the device; when that list changes, the consent has to be asked again, and
 *    a bare `"1"` cannot express that.
 *
 * The names are the appendix's own (§0.5) and are kept verbatim.
 */
internal object SecurityConsentPrefs {
    /** `true` accepted, `false` rejected, absent never answered. */
    val SCAN_DATA_CONSENT = booleanPreferencesKey("scan_data_consent")

    /** The version of the disclosure text that was answered. Absent means the same as never asked. */
    val SCAN_CONSENT_VERSION = intPreferencesKey("scan_consent_version")
}
