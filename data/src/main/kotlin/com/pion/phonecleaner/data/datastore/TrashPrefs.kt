package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * The one key `data/trash/TrashRoots.kt` owns: the rename-probe's verdict, cached per volume so the
 * probe — one create, one rename, one delete — runs at most once per volume per install
 * (`TrashRoots`'s own KDoc explains why there is a probe at all).
 *
 * A `Set<String>` of `"<volumeRootPath>=app"` / `"<volumeRootPath>=shared"` entries rather than a
 * `Map`: `Preferences` has no map type, and this is the same shape `StorageAccessPrefs` already uses
 * for a per-item cache (`LLM.md` §4, "typed accessors per concern, never a god-repository").
 */
internal object TrashPrefs {
    val ROOT_MODE = stringSetPreferencesKey("trash_root_mode")
}
