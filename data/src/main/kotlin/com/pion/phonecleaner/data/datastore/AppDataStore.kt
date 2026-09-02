package com.pion.phonecleaner.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * The ONE `DataStore<Preferences>` instance in the process (`docs/system-architecture.md` §5.3, §7.3).
 *
 * The competitor keeps two `SharedPreferences` files — `od.d0` (`<pkg>`) and `od.g0` (`<pkg>.notify`) —
 * and **every write is `commit()` on the calling thread** (`java/od/d0.java:78-81`). Recording one
 * feature use rewrites all twenty `flux_gn_use_flux_*` keys that way, inside the frame budget of a
 * screen transition, on every launch of every feature (§7.3 item 1).
 *
 * ### The two rules that come with this file
 *
 * 1. **One instance.** `preferencesDataStore` is a property delegate: a second delegate over the same
 *    file name throws `IllegalStateException` at first access, and a second delegate over a *different*
 *    name splits the app's state in two the way `od.d0` + `od.g0` already did. Every accessor below,
 *    and every accessor a cluster adds, reads THIS store — injected as `DataStore<Preferences>` from
 *    `coreModule`, never re-created.
 * 2. **No god-repository.** Keys live in one `<Concern>Prefs.kt` file per concern in this package
 *    (`LLM.md` §4, §3.6), owned by the cluster that reads them. A concern gets a file of top-level or
 *    `object`-scoped `Preferences.Key<*>` values and nothing else — the reading and writing lives in
 *    that concern's repository, so a key table never grows a behaviour.
 *
 * A cluster adding persistence writes `data/datastore/<Concern>Prefs.kt`, adds no second `DataStore`,
 * and declares its repository in its own `<cluster>DataModule` (§5.7).
 */
private val Context.phoneCleanerPreferences: DataStore<Preferences> by preferencesDataStore(
    name = DATA_STORE_NAME,
)

/**
 * The single store, reached exactly once — by `coreModule`'s
 * `single<DataStore<Preferences>> { androidContext().appDataStore }`. Nothing else calls this.
 */
val Context.appDataStore: DataStore<Preferences>
    get() = phoneCleanerPreferences

/**
 * kebab-case, per `LLM.md` §5's rule for non-source artefacts. DataStore appends `.preferences_pb`.
 *
 * It is deliberately NOT one of the competitor's two file names: `LegacyPrefsMigrationWorker` reads
 * those once and never again (§7.3), so sharing a name would make the migration read its own output.
 */
private const val DATA_STORE_NAME = "phone-cleaner-prefs"
