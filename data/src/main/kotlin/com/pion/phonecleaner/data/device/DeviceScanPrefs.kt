package com.pion.phonecleaner.data.device

import androidx.datastore.preferences.core.intPreferencesKey

/**
 * The device cluster's one persisted key.
 *
 * It lives beside its repository rather than in `data/datastore/`, where `AppDataStore`'s second rule
 * puts a `<Concern>Prefs.kt`: that package belongs to another owner in this batch, and a key table is
 * not worth a cross-owner edit. It still reads the **same single** `DataStore<Preferences>` injected
 * from `coreModule`; no second store is created, which is the rule that actually matters.
 *
 * The stored value is the **local** epoch day, an `Int`. The competitor stores a `yyyy-MM-dd` string
 * formatted over a **GMT** calendar, so its badge clears at 07:00 for a UTC+7 user and at 19:00 the
 * previous day for a UTC−5 one (`docs/screens/18-device-battery-and-apps.md` §6.4).
 *
 * UNKNOWN — the key string is ours, not the competitor's. Deliberately: `LegacyPrefsMigrationWorker`
 * reads `flux_running_apps_last_scan_ymd` once, and sharing the name would make the migration read
 * its own output.
 */
internal object DeviceScanPrefs {
    val RUNNING_APPS_LAST_SCAN_EPOCH_DAY = intPreferencesKey("device_running_apps_last_scan_epoch_day")
}
