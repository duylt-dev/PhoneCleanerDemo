package com.pion.phonecleaner.core.ui.di

import com.pion.phonecleaner.core.ui.icon.AppIconLoader
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `coreUiModule` — LLM.md §6.1, and `docs/screens/21` §6.2.
 *
 * It declares exactly one thing, and the reason it declares that one thing *here* matters more than
 * the binding does: **`AppIconLoader` was declared four times across four cluster reports, under
 * three different names.** Koin overrides silently by load order, so two `single`s of one type are a
 * runtime coin flip rather than a compile error (LLM.md §6.4). One type, one declaring module.
 *
 * Nothing else belongs here. `ByteFormatter`, `FeatureCatalog`, `SensitivePermissionCatalog` and
 * `FeatureDescriptors` are pure, dependency-free objects and are deliberately **not** in Koin
 * (LLM.md §6.3, §12): injecting them buys nothing, and `ByteFormatter` has to work inside a
 * `RemoteViews` build and a `CoroutineWorker` where nothing is injected at all.
 *
 * `androidContext()` supplies the **application** context. Nothing below a Route ever takes an
 * Activity context — the competitor's adapter base holds one that is nullable in practice, and its
 * two call sites disagree about whether it can be (`docs/screens/21` §2.5 L7, §6.6 K2).
 */
val coreUiModule = module {
    single { AppIconLoader(androidContext()) }
}
