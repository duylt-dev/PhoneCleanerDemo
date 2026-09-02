package com.pion.phonecleaner.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * The key table for the settings cluster (`LLM.md` §4, §3.6). Keys only — the reading and the
 * writing live in the repositories in `data/settings/`, so a key table never grows a behaviour.
 *
 * The competitor keeps the locale in **four** places: two keys in its app prefs, two in
 * `language_setting.xml`, one in `Utils.xml`, plus the live `Configuration`
 * (`docs/screens/20-settings-language-and-push.md` §2.4 delta 2). Two keys here, and one reader each.
 *
 * UNKNOWN — the key strings are ours, not the competitor's. Its plaintext name
 * `flux_locale_flux_lang` (`docs/reverse-engineering/20-settings-language-and-push.md:616`) is
 * deliberately **not** reused, so a future `LegacyPrefsMigrationWorker` cannot read its own output.
 */
internal object SettingsPrefs {

    /**
     * The applied BCP-47 language tag; absent means "follow the system".
     *
     * This is a **pre-API-33 fallback copy**, not the source of truth: with
     * `AppCompatDelegate.setApplicationLocales` the framework is the store
     * (`docs/screens/20-settings-language-and-push.md` §8 open item 3). It exists because `minSdk`
     * is 28 and the picker must still render the applied row on a device the framework does not
     * persist for.
     */
    val LANGUAGE_TAG = stringPreferencesKey("settings_language_tag")

    /**
     * The resident status-bar widget's opt-in switch. **Absent means OFF** — the default is the
     * owner decision, so it is expressed as the absence of a value and never written on install.
     */
    val RESIDENT_WIDGET_ENABLED = booleanPreferencesKey("settings_resident_widget_enabled")
}
