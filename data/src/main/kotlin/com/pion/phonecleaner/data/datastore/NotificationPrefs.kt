package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

/**
 * The key table for the notification cluster (`LLM.md` §3.6, `docs/system-architecture.md` §7.3).
 *
 * It replaces `od.g0`, the competitor's **second** `SharedPreferences` file (`<pkg>.notify`), whose
 * every write is a `commit()` on the calling thread — including the system binder thread, twice per
 * posted notification (`docs/screens/17-notification-and-permissions.md` §5.2). There is one DataStore
 * in this process and these keys live in it.
 *
 * A key table only: the reading and the writing live in the cluster's repositories, so a key table
 * never grows a behaviour (`AppDataStore` rule 2).
 *
 * UNKNOWN — the key strings. `od.g0`'s own names are not recovered anywhere in
 * `docs/reverse-engineering/17-notification-and-permissions.md`, and they must not be reused in any
 * case: `LegacyPrefsMigrationWorker` reads the competitor's two files once, and sharing a name would
 * make the migration read its own output (`AppDataStore`). These names are ours.
 *
 * `g0.b()`'s removal of `notify_sp` has **no port**: nothing writes or reads that key, so it is dead
 * migration residue executed on every grant (§1.5). Appendix §6 item 2 records it as UNKNOWN.
 */
internal object NotificationPrefs {

    /**
     * The master hiding switch. Absent means `true` — parity with the competitor's default
     * (`docs/screens/17-notification-and-permissions.md` §2.1).
     */
    val HIDING_MASTER_ENABLED = booleanPreferencesKey("notification_hiding_master_enabled")

    /**
     * Packages the user opted in, as a set — never a JSON blob.
     *
     * `od.e0.h(pkg)` asks `rawJson.contains("\"$pkg\"")` against a serialised list, so a quoted token
     * containing the query matches. A `Set<String>` cannot be asked that question.
     */
    val HIDING_ENABLED_PACKAGES = stringSetPreferencesKey("notification_hiding_enabled_packages")

    /**
     * Epoch millis of the last id-900 summary post — the 10-minute throttle of `od.i.G`.
     *
     * The competitor advances it **inside** the `try`, after the post, so a throw leaves it
     * un-advanced and the next intercepted notification retries immediately, in a loop (§5.2). Ours is
     * written in a `finally`.
     */
    val SUMMARY_LAST_POSTED_AT = longPreferencesKey("notification_summary_last_posted_at")

    /**
     * `od.d0`'s `sens_app_count`: how many installed apps hold at least one sensitive permission.
     *
     * Written at the end of every successful permission scan and refresh, never in a destroy callback
     * (§4.5).
     */
    val SENSITIVE_APP_COUNT = intPreferencesKey("permission_sensitive_app_count")
}
