package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * The key table for persisted Storage Access Framework tree grants.
 *
 * `docs/system-architecture.md` §8.4 states the rule this exists for: *"Persisted SAF grants live in
 * DataStore and are re-validated on read"* — a `takePersistableUriPermission` grant survives a reboot
 * but not an uninstall and not a user revocation, so the reader drops an invalid tree rather than
 * returning an empty list that looks like success (§7.6).
 *
 * The re-validation is in `AndroidStorageRootProvider`; this file holds the key and nothing else.
 */
internal object StorageAccessPrefs {
    /** `content://` tree URIs the user has granted, as strings. Absent means "none granted". */
    val PERSISTED_TREE_URIS = stringSetPreferencesKey("storage_persisted_tree_uris")
}
