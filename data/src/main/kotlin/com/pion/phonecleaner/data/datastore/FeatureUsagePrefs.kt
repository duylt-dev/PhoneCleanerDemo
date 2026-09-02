package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.pion.phonecleaner.domain.model.feature.FeatureId

/**
 * The key table for "when was this feature last opened" — one key per [FeatureId], and nothing else.
 *
 * The competitor spends 538 lines on this (`ae.o0`: 20 fields, 20 getters, 20 setters, 20
 * `updateXAndSave()`) plus a 119-line static cache (`qd.a`), and one recorded use rewrites all twenty
 * keys with `commit()` (`docs/screens/21-shared-models-and-ui.md` §5.4 G1, G6). Here the enum is the
 * key, so `markUsed` writes exactly one.
 *
 * UNKNOWN — the key *string*. No document in the corpus names our key names; the only names that
 * exist are the competitor's, carried on [FeatureId.legacyPrefKey] for the one worker allowed to read
 * them (`docs/system-architecture.md` §7.3, "Migration runs once"). Looked in system-architecture §5.3
 * / §5.4 / §7.3, `docs/screens/21` §5.3 and `docs/screens/11` §Koin. The scheme below is derived from
 * the enum, so it cannot drift from it — which is the defect G4 records for the competitor's
 * hand-maintained 20-arm `switch`.
 */
internal object FeatureUsagePrefs {

    /** Epoch milliseconds of the last recorded use. Absent means "never used", not "used at 0". */
    fun lastUsedAt(feature: FeatureId): Preferences.Key<Long> =
        longPreferencesKey("$LAST_USED_PREFIX${feature.name}")

    /** Every key this table owns, in enum order — the read side of the twenty. */
    val allLastUsedKeys: List<Preferences.Key<Long>>
        get() = FeatureId.entries.map(::lastUsedAt)

    private const val LAST_USED_PREFIX = "feature_last_used_at_"
}
