package com.pion.phonecleaner.domain.di

import com.pion.phonecleaner.domain.usecase.CleanFilesUseCase
import com.pion.phonecleaner.domain.usecase.CleanJunkUseCase
import com.pion.phonecleaner.domain.usecase.ClearAppLockUseCase
import com.pion.phonecleaner.domain.usecase.ClearHiddenNotificationsUseCase
import com.pion.phonecleaner.domain.usecase.CompressPhotosUseCase
import com.pion.phonecleaner.domain.usecase.DeleteFilesUseCase
import com.pion.phonecleaner.domain.usecase.DeletePhotosUseCase
import com.pion.phonecleaner.domain.usecase.DismissHiddenNotificationUseCase
import com.pion.phonecleaner.domain.usecase.EstimateCompressionUseCase
import com.pion.phonecleaner.domain.usecase.FindDuplicatesUseCase
import com.pion.phonecleaner.domain.usecase.GetTrafficReportUseCase
import com.pion.phonecleaner.domain.usecase.GroupAppsByPermissionUseCase
import com.pion.phonecleaner.domain.usecase.IgnoreFindingUseCase
import com.pion.phonecleaner.domain.usecase.ListStoppableAppsUseCase
import com.pion.phonecleaner.domain.usecase.LoadAlbumPhotosUseCase
import com.pion.phonecleaner.domain.usecase.LoadAlbumsUseCase
import com.pion.phonecleaner.domain.usecase.LoadAudioUseCase
import com.pion.phonecleaner.domain.usecase.LoadCompressiblePhotosUseCase
import com.pion.phonecleaner.domain.usecase.LoadGeotaggedPhotosUseCase
import com.pion.phonecleaner.domain.usecase.LoadInstalledAppsUseCase
import com.pion.phonecleaner.domain.usecase.LoadVideosUseCase
import com.pion.phonecleaner.domain.usecase.MarkFeatureUsedUseCase
import com.pion.phonecleaner.domain.usecase.MarkRunningAppsScannedUseCase
import com.pion.phonecleaner.domain.usecase.MimeTypeUseCase
import com.pion.phonecleaner.domain.usecase.ObserveAppLockSettingsUseCase
import com.pion.phonecleaner.domain.usecase.ObserveBatteryUseCase
import com.pion.phonecleaner.domain.usecase.ObserveHiddenNotificationsUseCase
import com.pion.phonecleaner.domain.usecase.ObserveLockableAppsUseCase
import com.pion.phonecleaner.domain.usecase.ObserveNotificationHidingSettingsUseCase
import com.pion.phonecleaner.domain.usecase.ReadDeviceMetricsUseCase
import com.pion.phonecleaner.domain.usecase.ReadMemoryUseCase
import com.pion.phonecleaner.domain.usecase.ReadUsageAccessUseCase
import com.pion.phonecleaner.domain.usecase.RefreshAppPermissionsUseCase
import com.pion.phonecleaner.domain.usecase.RemoveFindingUseCase
import com.pion.phonecleaner.domain.usecase.RunSpeedTestUseCase
import com.pion.phonecleaner.domain.usecase.SavePinUseCase
import com.pion.phonecleaner.domain.usecase.ScanAppPermissionsUseCase
import com.pion.phonecleaner.domain.usecase.ScanBigFilesUseCase
import com.pion.phonecleaner.domain.usecase.ScanBlurryPhotosUseCase
import com.pion.phonecleaner.domain.usecase.ScanSimilarPhotosUseCase
import com.pion.phonecleaner.domain.usecase.ScanWhatsAppUseCase
import com.pion.phonecleaner.domain.usecase.SetAppLockEnabledUseCase
import com.pion.phonecleaner.domain.usecase.SetAppLockedUseCase
import com.pion.phonecleaner.domain.usecase.SetLockNewlyInstalledUseCase
import com.pion.phonecleaner.domain.usecase.SetNotificationHidingUseCase
import com.pion.phonecleaner.domain.usecase.StripPhotoLocationUseCase
import com.pion.phonecleaner.domain.usecase.UninstallAppUseCase
import com.pion.phonecleaner.domain.usecase.VerifyAppStoppedUseCase
import com.pion.phonecleaner.domain.usecase.VerifyPinUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * Every use case, and nothing else.
 *
 * **`factoryOf(::XUseCase)`, without exception.** A use case is stateless and cheap; a `single` use
 * case is a shared object that outlives the screen for no benefit
 * (`docs/system-architecture.md` §5.8).
 *
 * A use case is registered here **in the same change that adds it** (`LLM.md` §6.5). Repositories are
 * not declared here: they belong to `coreDataModule`, `storageDataModule` or a cluster's own data
 * module, and a second declaration of one is a load-order coin flip at runtime rather than a compile
 * error (`LLM.md` §6.4).
 *
 * `FeatureCatalog` and `SensitivePermissionCatalog` are deliberately absent: pure, dependency-free
 * objects gain nothing from injection (`LLM.md` §6.3).
 *
 * The list below is exhaustive over `domain/usecase/` by construction — it was generated from that
 * directory, so a use case that exists but resolves to nothing cannot hide here. `KoinModulesTest`
 * is what keeps it honest.
 */
val domainModule = module {
    factoryOf(::CleanFilesUseCase)
    factoryOf(::CleanJunkUseCase)
    factoryOf(::ClearAppLockUseCase)
    factoryOf(::ClearHiddenNotificationsUseCase)
    factoryOf(::CompressPhotosUseCase)
    factoryOf(::DeleteFilesUseCase)
    factoryOf(::DeletePhotosUseCase)
    factoryOf(::DismissHiddenNotificationUseCase)
    factoryOf(::EstimateCompressionUseCase)
    factoryOf(::FindDuplicatesUseCase)
    factoryOf(::GetTrafficReportUseCase)
    factoryOf(::GroupAppsByPermissionUseCase)
    factoryOf(::IgnoreFindingUseCase)
    factoryOf(::ListStoppableAppsUseCase)
    factoryOf(::LoadAlbumPhotosUseCase)
    factoryOf(::LoadAlbumsUseCase)
    factoryOf(::LoadAudioUseCase)
    factoryOf(::LoadCompressiblePhotosUseCase)
    factoryOf(::LoadGeotaggedPhotosUseCase)
    factoryOf(::LoadInstalledAppsUseCase)
    factoryOf(::LoadVideosUseCase)
    factoryOf(::MarkFeatureUsedUseCase)
    factoryOf(::MarkRunningAppsScannedUseCase)
    factoryOf(::MimeTypeUseCase)
    factoryOf(::ObserveAppLockSettingsUseCase)
    factoryOf(::ObserveBatteryUseCase)
    factoryOf(::ObserveHiddenNotificationsUseCase)
    factoryOf(::ObserveLockableAppsUseCase)
    factoryOf(::ObserveNotificationHidingSettingsUseCase)
    factoryOf(::ReadDeviceMetricsUseCase)
    factoryOf(::ReadMemoryUseCase)
    factoryOf(::ReadUsageAccessUseCase)
    factoryOf(::RefreshAppPermissionsUseCase)
    factoryOf(::RemoveFindingUseCase)
    factoryOf(::RunSpeedTestUseCase)
    factoryOf(::SavePinUseCase)
    factoryOf(::ScanAppPermissionsUseCase)
    factoryOf(::ScanBigFilesUseCase)
    factoryOf(::ScanBlurryPhotosUseCase)
    factoryOf(::ScanSimilarPhotosUseCase)
    factoryOf(::ScanWhatsAppUseCase)
    factoryOf(::SetAppLockEnabledUseCase)
    factoryOf(::SetAppLockedUseCase)
    factoryOf(::SetLockNewlyInstalledUseCase)
    factoryOf(::SetNotificationHidingUseCase)
    factoryOf(::StripPhotoLocationUseCase)
    factoryOf(::UninstallAppUseCase)
    factoryOf(::VerifyAppStoppedUseCase)
    factoryOf(::VerifyPinUseCase)
}
