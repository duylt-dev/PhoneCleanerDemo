package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.longPreferencesKey

/**
 * The key table for the lifetime "space freed" ledger (`docs/system-architecture.md` §5.4).
 *
 * The competitor's equivalent is `md.g4` over `flux_sp_key_total_saved_bytes`
 * (`docs/reverse-engineering/02-cross-cutting-permissions-storage-analytics.md:140`). Its accumulator
 * is fed by `g4.e()`, which **parses the formatted display string back into a `Long`** — losing about
 * 5 % to `DecimalFormat("###.0")` and defaulting an unrecognised unit to MB
 * (`docs/reverse-engineering/12-junk-cleaning.md:53`). Ours is fed a raw `Long` by the use case that
 * actually freed the bytes (`docs/screens/14-file-tools-and-app-manager.md:926`).
 *
 * UNKNOWN — the key string; see [FeatureUsagePrefs] for where that was looked for. The name below is
 * ours, and is deliberately not the competitor's, so the migration worker cannot read its own output.
 */
internal object CleanupLedgerPrefs {
    /** Monotonically increasing total of bytes this app has freed. Absent means "nothing yet". */
    val LIFETIME_FREED_BYTES = longPreferencesKey("cleanup_lifetime_freed_bytes")
}
