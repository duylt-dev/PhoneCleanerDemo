package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.push.InertPushRepository
import com.pion.phonecleaner.data.settings.AndroidAppInfoProvider
import com.pion.phonecleaner.data.settings.DataStoreLanguageRepository
import com.pion.phonecleaner.data.settings.DataStoreResidentWidgetSettings
import com.pion.phonecleaner.data.settings.DefaultLegalDocumentUrls
import com.pion.phonecleaner.domain.repository.AppInfoProvider
import com.pion.phonecleaner.domain.repository.LanguageRepository
import com.pion.phonecleaner.domain.repository.LegalDocumentUrls
import com.pion.phonecleaner.domain.repository.PushRepository
import com.pion.phonecleaner.domain.repository.ResidentWidgetSettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `settingsDataModule` — the settings cluster's `:data` bindings
 * (`docs/screens/20-settings-language-and-push.md` §7, `LLM.md` §6.1).
 *
 * ### What this module must NOT declare (`LLM.md` §6.4)
 *
 * A second `single<X>` is a **silent runtime override decided by module load order**, not a compile
 * error. This cluster's own research declared three of the bindings below and lost every one of them
 * in `docs/system-architecture.md` §4.1/§5.1; the winners are consumed with `get()`, never redeclared.
 *
 * | Binding | Its one home |
 * |---|---|
 * | `PermissionRepository` | `coreDataModule` — three clusters declared it, under four names |
 * | `AnalyticsRepository` | `coreDataModule` — nine clusters wanted it |
 * | `DataStore<Preferences>` · `DispatcherProvider` · `AppLogger` · `AppClock` | `coreModule` |
 * | `AppLifecycleObserver` | `coreDataModule` |
 * | `ByteFormatter` · `FeatureCatalog` | nothing — pure objects, not in Koin (§6.3) |
 */
val settingsDataModule = module {

    /**
     * The picker's one port. It reads the shared `DataStore` and never a second one: the competitor
     * keeps the locale in four places that agree only because one method writes them together
     * (`docs/screens/20-settings-language-and-push.md` §2.4 delta 2).
     */
    single<LanguageRepository> { DataStoreLanguageRepository(get(), get(), get()) }

    /** Four values read from `PackageManager` once. Replaces a hand-typed version literal. */
    single<AppInfoProvider> { AndroidAppInfoProvider(androidContext()) }

    /** One source for the two legal URLs, in place of the competitor's three copies of two strings. */
    single<LegalDocumentUrls> { DefaultLegalDocumentUrls() }

    /**
     * PENDING OWNER DECISION 4, and a **binding that must be reconciled, not duplicated**.
     *
     * §7.1 assigns `ResidentWidgetSettingsRepository` to `backgroundModule`, which does not exist
     * yet; §8 open item 1 records that the name is not in `docs/system-architecture.md` §4.1's alias
     * table because the widget became opt-in after the research closed. It is declared here so the
     * settings switch works at all. **When `backgroundModule` is written, move this line — do not
     * add a second one:** `WidgetRefreshWorker` and this switch must read the same flag, and two
     * `single`s over one key is a load-order coin flip (`LLM.md` §6.4).
     */
    single<ResidentWidgetSettingsRepository> { DataStoreResidentWidgetSettings(get(), get(), get()) }

    /**
     * Likewise `backgroundModule`'s by §7.1, and likewise homeless until it exists. `PushRepository`
     * is bound here because `PushMessagingService` — a manifest component in this module — resolves
     * it, and because the debug bench drives the same method. **Move, do not duplicate.**
     */
    single<PushRepository> { InertPushRepository(get()) }
}
