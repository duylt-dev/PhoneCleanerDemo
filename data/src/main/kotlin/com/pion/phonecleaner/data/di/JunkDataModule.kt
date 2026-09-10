package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.junk.DefaultJunkDeleter
import com.pion.phonecleaner.data.junk.DefaultJunkRepository
import com.pion.phonecleaner.data.junk.InMemoryJunkSessionStore
import com.pion.phonecleaner.data.junk.JunkEstimateCache
import com.pion.phonecleaner.data.junk.KotlinJunkRuleCatalog
import com.pion.phonecleaner.data.junk.RuleJunkScanner
import com.pion.phonecleaner.domain.repository.JunkDeleter
import com.pion.phonecleaner.domain.repository.JunkRepository
import com.pion.phonecleaner.domain.repository.JunkRuleCatalog
import com.pion.phonecleaner.domain.repository.JunkScanner
import com.pion.phonecleaner.domain.repository.JunkSessionStore
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * `junkDataModule` — the junk cluster's own `:data` components
 * (`docs/screens/12-junk-cleaning.md` §7.1).
 *
 * ### Every type below is named by no other cluster — which is what makes these `single`s legal
 *
 * `LLM.md` §6.4: **a shared type is declared exactly once, in the module that owns its layer, and a
 * per-cluster module may declare only types no other cluster names.** Koin overrides silently by
 * default, so a second `single<X>` is a load-order coin flip at runtime, not a compile error.
 *
 * §7.3 of the appendix lists what `r2-03` §B1.4 proposed for this module and what actually owns each
 * one. **None of these is declared here:**
 *
 * | Proposed for the junk module | Actually owned by |
 * |---|---|
 * | `StorageRootProvider`, `DirectorySizer`, `FileDeleter`, `StorageScanner` | `storageDataModule` — shared file primitives; five clusters need them |
 * | `CleanupLedger` | `coreDataModule` — clusters 03 and 05 both declared it |
 * | `PermissionRepository` (proposed as `StorageAccess`) | `coreDataModule` — clusters 02, 05 and 11 also declared it |
 * | `InstalledAppsRepository` (proposed as `InstalledPackages`) | `coreDataModule` — clusters 05, 07, 08, 09 also declared it |
 * | `AnalyticsRepository` (proposed as `AnalyticsTracker`) | `coreDataModule` — nine reports, four names, one binding |
 * | `ByteFormatter` | **nothing** — a stateless, platform-free `object` gains nothing from injection |
 * | `DataStore<Preferences>`, `AppClock`, `DispatcherProvider`, the app scope | `coreModule` |
 *
 * WIRING — this module is not yet in `:app/App.kt`'s `appModules`; adding it belongs to whoever owns
 * that file. Nothing in the junk cluster resolves until it is there.
 */
val junkDataModule = module {

    // PENDING OWNER DECISION (1) is CLOSED (2026-09-06): branch B, our own rules in code. The
    // competitor's 41-rule / 208-app table is not carried and may not be added — see the class KDoc
    // for the reason and for what that costs. Swapping this one line is still the whole change.
    single<JunkRuleCatalog> { KotlinJunkRuleCatalog() }

    // The fifth `get()` is `InstalledAppsRepository`, declared once in `coreDataModule`. Pass 2's
    // rule is inverted — a residual folder is junk only when its app is GONE — so the scanner cannot
    // answer it without the installed list.
    single<JunkScanner> { RuleJunkScanner(get(), get(), get(), get(), get(), get()) }

    single<JunkDeleter> { DefaultJunkDeleter(androidContext(), get(), get()) }

    single<JunkSessionStore> { InMemoryJunkSessionStore(get()) }

    // The app scope, so a walk started for the badge is not tied to whichever screen asked for it.
    single { JunkEstimateCache(get(), get(), get(), get(named(APP_SCOPE))) }

    single<JunkRepository> { DefaultJunkRepository(get(), get(), get()) }
}
