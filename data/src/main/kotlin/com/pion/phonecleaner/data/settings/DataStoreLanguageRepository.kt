package com.pion.phonecleaner.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.pion.phonecleaner.core.common.concurrent.DispatcherProvider
import com.pion.phonecleaner.core.common.error.AppError
import com.pion.phonecleaner.core.common.log.AppLogger
import com.pion.phonecleaner.core.common.result.AppResult
import com.pion.phonecleaner.data.datastore.SettingsPrefs
import com.pion.phonecleaner.domain.model.settings.AppLanguage
import com.pion.phonecleaner.domain.repository.LanguageRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [LanguageRepository] over the one `DataStore<Preferences>`.
 *
 * ### Why a stored copy exists at all
 *
 * `AppCompatDelegate.setApplicationLocales` persists the choice itself, so the framework **is** the
 * store (`docs/screens/20-settings-language-and-push.md` §2.4 delta 2). This file keeps one key as
 * the **pre-API-33 fallback** §8 open item 3 describes, and is its only reader — which is the whole
 * point: the competitor reads the same two keys from `od.p0.k()` and `od.p0.g()` with two different
 * fallback policies (`""` in one, `Locale.getDefault().getLanguage()` in the other,
 * `java/od/p0.java:171-182`, `:206-217`), so the answer depends on which method you happened to call.
 *
 * It does **not** read the live `Configuration`: that would be a second source of truth, and reading
 * it here would put an Android locale type behind a port whose whole job is to keep one out of the
 * ViewModel.
 *
 * UNKNOWN — whether the framework's own `AppCompatDelegate.getApplicationLocales()` should be read
 * back as the primary source once `:app` ships `android:localeConfig`. Looked for a stated read path
 * in `docs/screens/20-settings-language-and-push.md` §2.2 (which specifies only
 * `currentLanguage()`), §2.4 delta 2 ("keep a DataStore copy only for a pre-API-33 fallback, and make
 * the repository its only reader") and §8 open item 3, which leaves it at "no DataStore copy unless a
 * concrete failure demands one". Reading it would require `androidx.appcompat` in `:data`, which is
 * catalogued for the *picker's* platform call only; the write below is what keeps the two in step.
 */
internal class DataStoreLanguageRepository(
    private val dataStore: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
    private val log: AppLogger,
) : LanguageRepository {

    /** A constant list: no IO, no dispatcher, nothing to fail. */
    override fun supportedLanguages(): ImmutableList<AppLanguage> = SupportedLanguages.all

    /**
     * `null` — follow the system — for an absent key **and** for a stored tag no longer in the
     * roster. A tag that was dropped from the list must not leave the picker with nothing selected,
     * which is exactly the competitor's silent "no radio filled" case (§2.4 delta 4).
     */
    override fun currentLanguage(): Flow<AppLanguage?> = dataStore.data
        .map { SupportedLanguages.byTag(it[SettingsPrefs.LANGUAGE_TAG]) }
        .distinctUntilChanged()
        .flowOn(dispatchers.io)

    override suspend fun setLanguage(tag: String?): AppResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            dataStore.edit { prefs ->
                if (tag == null) {
                    prefs.remove(SettingsPrefs.LANGUAGE_TAG)
                } else {
                    prefs[SettingsPrefs.LANGUAGE_TAG] = tag
                }
            }
            Unit
        }.fold(
            onSuccess = { AppResult.Success(it) },
            onFailure = {
                log.e(it) { "Language preference could not be written" }
                AppResult.Failure(AppError.Storage(cause = it.message))
            },
        )
    }
}
