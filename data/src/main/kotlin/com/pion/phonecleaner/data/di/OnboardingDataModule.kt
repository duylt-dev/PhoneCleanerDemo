package com.pion.phonecleaner.data.di

import com.pion.phonecleaner.data.onboarding.DataStoreOnboardingStateRepository
import com.pion.phonecleaner.data.onboarding.PlatformDeviceCheckProbe
import com.pion.phonecleaner.data.onboarding.UmpConsentRepository
import com.pion.phonecleaner.domain.model.onboarding.AppResumePacing
import com.pion.phonecleaner.domain.model.onboarding.DeviceCheckPacing
import com.pion.phonecleaner.domain.model.onboarding.DeviceCheckProbe
import com.pion.phonecleaner.domain.model.onboarding.SplashPacing
import com.pion.phonecleaner.domain.repository.ConsentRepository
import com.pion.phonecleaner.domain.repository.OnboardingStateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * `onboardingDataModule` — the onboarding cluster's `:data` bindings.
 *
 * **This module did not exist.** `docs/system-architecture.md` §5.7 lists ten per-cluster `:data`
 * modules and none of them is an onboarding one, which left four `single`s with no declared home —
 * `docs/screens/10-splash-and-onboarding.md` §1.4 and §5.3 open item 1 both record the gap. Putting
 * them into an existing module instead would be the duplicate-binding class of defect the audit
 * exists to prevent, and putting them into `onboardingModule` is illegal outright: a `:feature`
 * module declares `viewModel` and nothing else (`LLM.md` §6.1).
 *
 * **It is not yet in `:app/App.kt`'s `appModules`.** That file is the one assembly point (§6.2) and
 * is not this change's to edit; the line to add is reported with the change. Until it is added, the
 * three onboarding ViewModels cannot resolve.
 *
 * ### Everything this cluster consumes and does NOT declare (`LLM.md` §6.4)
 *
 * | Type | Its one declaring module |
 * |---|---|
 * | `DataStore<Preferences>` · `DispatcherProvider` · `AppLogger` | `coreModule` |
 * | `StorageInfoRepository` | `storageDataModule` |
 *
 * `DeviceCheckProbe` below is declared here and **not** named `DeviceMetricsRepository`: that type
 * belongs to the device cluster and `docs/screens/10-splash-and-onboarding.md` §3.4 says
 * "`deviceDataModule` — do not redeclare". A per-cluster module may declare only types no other
 * cluster names (§6.4), and this narrow five-field port is one.
 *
 * Six independently written cluster designs each declared their own `single<FeatureUsageRepository>`,
 * three declared `single<PermissionRepository>` and four `single<InstalledAppsRepository>`. Koin
 * resolves a duplicate by module load order, silently, at runtime (§6.4). Nothing on the shared list
 * is repeated below.
 */
val onboardingDataModule = module {

    /**
     * The first-run latches. `docs/screens/11-home.md:500`'s open `AppSettingsRepository` —
     * `hasCompletedFirstRun` / `hasAcceptedTerms` — is **these two fields of `OnboardingState`**, not
     * a second binding: two `single`s over one pair of DataStore keys is two writers and a load-order
     * coin flip.
     */
    single<OnboardingStateRepository> { DataStoreOnboardingStateRepository(get(), get()) }

    /** The consent seam. The SDK behind it is out of scope; see the class for what integrating means. */
    single<ConsentRepository> { UmpConsentRepository(get()) }

    /**
     * The five device-check readings. `StorageInfoRepository` is `storageDataModule`'s and is
     * injected here, never re-implemented — the storage branch lives in exactly one place.
     */
    single<DeviceCheckProbe> { PlatformDeviceCheckProbe(androidContext(), get(), get()) }

    // The three pacing records. They are `single` and not plain objects because a test constructs its
    // own (`docs/screens/10-splash-and-onboarding.md` §1.2) and because the values are configuration:
    // the splash's are fixed by docs/system-architecture.md:1275, the device check's by §3.5 delta 4.
    single { SplashPacing() }
    single { AppResumePacing() }
    single { DeviceCheckPacing() }
}
