package com.pion.phonecleaner

import android.app.Application
import com.pion.phonecleaner.core.ui.di.coreUiModule
import com.pion.phonecleaner.data.di.backgroundModule
import com.pion.phonecleaner.data.di.coreDataModule
import com.pion.phonecleaner.data.di.settingsDataModule
import com.pion.phonecleaner.data.di.notificationDataModule
import com.pion.phonecleaner.data.di.networkDataModule
import com.pion.phonecleaner.data.di.deviceDataModule
import com.pion.phonecleaner.data.di.appLockDataModule
import com.pion.phonecleaner.data.di.coreModule
import com.pion.phonecleaner.data.di.filesDataModule
import com.pion.phonecleaner.data.di.photoDataModule
import com.pion.phonecleaner.data.di.securityDataModule
import com.pion.phonecleaner.data.di.junkDataModule
import com.pion.phonecleaner.data.di.onboardingDataModule
import com.pion.phonecleaner.data.di.storageDataModule
import com.pion.phonecleaner.data.di.trashDataModule
import com.pion.phonecleaner.domain.di.domainModule
import com.pion.phonecleaner.data.log.AndroidAppLogger
import com.pion.phonecleaner.feature.antivirus.di.antivirusModule
import com.pion.phonecleaner.feature.applock.di.appLockModule
import com.pion.phonecleaner.feature.cleanresult.di.cleanResultModule
import com.pion.phonecleaner.feature.device.di.deviceModule
import com.pion.phonecleaner.feature.files.di.filesModule
import com.pion.phonecleaner.feature.home.di.homeModule
import com.pion.phonecleaner.feature.junk.di.junkModule
import com.pion.phonecleaner.feature.network.di.networkModule
import com.pion.phonecleaner.feature.notification.di.notificationModule
import com.pion.phonecleaner.feature.onboarding.di.onboardingModule
import com.pion.phonecleaner.feature.photo.di.photoModule
import com.pion.phonecleaner.feature.settings.di.settingsModule
import com.pion.phonecleaner.feature.trash.di.trashModule
import com.pion.phonecleaner.feature.vault.di.vaultModule
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.context.startKoin

/**
 * The one assembly point. `startKoin` is called here and nowhere else (LLM.md §6.2).
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()

        // The log gate is set by DIRECT ASSIGNMENT, before startKoin — never read through DI.
        // Read as `by inject(named(...))` it throws IllegalStateException from the first
        // BroadcastReceiver or SDK callback that beats startKoin, on a thread nothing wraps, so it
        // takes the process down rather than the log line (LLM.md §6.2, MVI doc §1).
        AndroidAppLogger.enabled = BuildConfig.DEBUG

        startKoin {
            androidContext(this@App)
            // BEFORE modules(): Koin registers its WorkerFactory on the KoinApplication, and a
            // module resolved first would have no factory to hand WorkManager.
            workManagerFactory()
            modules(appModules)
        }
    }
}

/**
 * Declared as a value rather than inline so `KoinModulesTest` can call `checkModules()` over exactly
 * what ships — a missing binding then fails the build instead of the screen (LLM.md §6.4).
 */
val appModules = listOf(
    // shared platform + UI
    coreModule,
    coreUiModule,
    // :data — shared. Load order and one-declaring-module-per-type are LLM.md §6.4/§6.5.
    coreDataModule,
    storageDataModule,
    // :data — per cluster. One module per cluster, declaring only types no other cluster names.
    onboardingDataModule,
    junkDataModule,
    photoDataModule,
    filesDataModule,
    securityDataModule,
    appLockDataModule,
    settingsDataModule,
    trashDataModule,
    notificationDataModule,
    networkDataModule,
    deviceDataModule,
    // :data — background work; the app's only WorkManager module
    backgroundModule,
    // :domain — every use case, all `factory`
    domainModule,
    // :feature:* — viewModel only. Empty until each cluster's screens land.
    onboardingModule,
    homeModule,
    junkModule,
    photoModule,
    filesModule,
    cleanResultModule,
    antivirusModule,
    appLockModule,
    vaultModule,
    notificationModule,
    deviceModule,
    networkModule,
    settingsModule,
    trashModule,
)
