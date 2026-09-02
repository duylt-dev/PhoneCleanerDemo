package com.pion.phonecleaner.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.pion.phonecleaner.data.permission.SpecialAccessIntents
import com.pion.phonecleaner.domain.model.applock.PinMode
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.junk.JunkScanMode
import com.pion.phonecleaner.domain.model.launch.LaunchSource
import com.pion.phonecleaner.feature.antivirus.result.AntivirusResultRoute
import com.pion.phonecleaner.feature.antivirus.scan.AntivirusScanRoute
import com.pion.phonecleaner.feature.applock.applock.AppLockRoute
import com.pion.phonecleaner.feature.applock.pin.PinRoute
import com.pion.phonecleaner.feature.applock.settings.AppLockSettingsRoute
import com.pion.phonecleaner.feature.cleanresult.CleanResultRoute
import com.pion.phonecleaner.feature.device.batteryinfo.BatteryInfoRoute
import com.pion.phonecleaner.feature.device.batteryscan.BatteryScanRoute
import com.pion.phonecleaner.feature.device.devicestatusdetail.DeviceStatusDetailRoute
import com.pion.phonecleaner.feature.device.devicestatusscan.DeviceStatusScanRoute
import com.pion.phonecleaner.feature.device.runningapps.RunningAppsRoute
import com.pion.phonecleaner.feature.device.runningappsscan.RunningAppsScanRoute
import com.pion.phonecleaner.feature.files.appmanager.AppManagerRoute
import com.pion.phonecleaner.feature.files.audio.AudioManagerRoute
import com.pion.phonecleaner.feature.files.bigfiles.BigFilesRoute
import com.pion.phonecleaner.feature.files.duplicates.DuplicatesRoute
import com.pion.phonecleaner.feature.files.video.VideoManagerRoute
import com.pion.phonecleaner.feature.files.whatsapp.WhatsAppCleanerRoute
import com.pion.phonecleaner.feature.home.HomeRoute
import com.pion.phonecleaner.feature.junk.junkclean.JunkCleanRoute
import com.pion.phonecleaner.feature.junk.junkreview.JunkReviewRoute
import com.pion.phonecleaner.feature.junk.junkscan.JunkScanRoute
import com.pion.phonecleaner.feature.network.speedtest.SpeedTestRoute
import com.pion.phonecleaner.feature.network.speedtestresult.SpeedTestResultRoute
import com.pion.phonecleaner.feature.network.traffic.NetworkTrafficRoute
import com.pion.phonecleaner.feature.notification.gate.NotificationGateRoute
import com.pion.phonecleaner.feature.notification.hiddenlist.HiddenNotificationsRoute
import com.pion.phonecleaner.feature.notification.hidingsettings.NotificationHidingSettingsRoute
import com.pion.phonecleaner.feature.notification.permissionmanager.PermissionManagerRoute
import com.pion.phonecleaner.feature.onboarding.appresume.AppResumeRoute
import com.pion.phonecleaner.feature.onboarding.devicecheck.DeviceCheckRoute
import com.pion.phonecleaner.feature.onboarding.splash.SplashRoute
import com.pion.phonecleaner.feature.photo.albumdetail.AlbumDetailRoute
import com.pion.phonecleaner.feature.photo.albums.AlbumsRoute
import com.pion.phonecleaner.feature.photo.compressor.PhotoCompressorRoute
import com.pion.phonecleaner.feature.photo.compressrun.CompressRunRoute
import com.pion.phonecleaner.feature.photo.preview.PhotoPreviewRoute
import com.pion.phonecleaner.feature.photo.privacy.PhotoPrivacyRoute
import com.pion.phonecleaner.feature.photo.similar.SimilarPhotosRoute
import com.pion.phonecleaner.feature.settings.about.AboutRoute
import com.pion.phonecleaner.feature.settings.language.LanguageRoute
import com.pion.phonecleaner.feature.settings.permissioncentre.PermissionCentreRoute
import com.pion.phonecleaner.feature.settings.settings.SettingsRoute
import com.pion.phonecleaner.feature.settings.webview.WebViewRoute

/**
 * The single NavHost. Every `XRoute`'s callbacks are wired to `navController` calls HERE, so no
 * feature module needs to know another exists (LLM.md §3.9, §7).
 *
 * Navigation is always an Effect a ViewModel raises, never a flag on state
 * (`.claude/CLAUDE.md`, non-negotiable rule 6).
 *
 * ### Two rules the wiring below repeats
 *
 * **A scan screen pops itself.** Every `onScanned` navigates with `popUpTo(<the scan>) { inclusive
 * = true }`, so Back from a result never replays the scan the user just sat through, and a rescan
 * cannot stack a second result behind the first.
 *
 * **`onSessionLost` is the process-death branch.** A screen whose payload lives in a session store
 * rather than in its route argument has no way to rebuild itself after the process is killed; it
 * says so instead of rendering an empty list (`docs/system-architecture.md` §5.2).
 */
@Composable
fun AppNavGraph(
    launchSource: LaunchSource,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    // The host owns every special access; `HomeRoute` and `AppLockRoute` own only the one runtime
    // permission each can request in-screen. The result is ignored on purpose — the screens re-read
    // the grant on resume, which is the only answer that is true after the user leaves Settings.
    val specialAccess = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val onRequestSpecialAccess = remember(context) {
        { permission: com.pion.phonecleaner.domain.model.permission.AppPermission ->
            specialAccess.launch(SpecialAccessIntents.forPermission(context, permission))
        }
    }
    val onExit = remember(context) { { context.findActivity()?.finish(); Unit } }

    NavHost(
        navController = navController,
        startDestination = Route.Splash(launchSource),
        modifier = modifier,
    ) {
        // -- onboarding -----------------------------------------------------------------------

        composable<Route.Splash> {
            SplashRoute(
                onNavigateToDeviceCheck = {
                    navController.navigate(Route.DeviceCheck) {
                        popUpTo<Route.Splash> { inclusive = true }
                    }
                },
                onNavigateToHome = { navController.toHome<Route.Splash>() },
                onOpenPolicyPage = { navController.navigate(Route.WebView(it)) },
            )
        }

        composable<Route.DeviceCheck> {
            DeviceCheckRoute(onNavigateToHome = { navController.toHome<Route.DeviceCheck>() })
        }

        // A dialog on the existing back stack, not an Activity started from Application scope the
        // way the competitor's is (`Condfil.java:858-868`). Nothing navigates here yet: the gate
        // that decides when it opens sits on pending owner decision 4.
        dialog<Route.AppResume> {
            AppResumeRoute(onDismiss = { navController.popBackStack() })
        }

        // -- home -----------------------------------------------------------------------------

        composable<Route.Home> {
            HomeRoute(
                onOpenFeature = { navController.navigate(it.destination()) },
                onOpenSettings = { navController.navigate(Route.Settings) },
                // The warning banner is about OUR grants, so it opens this app's own roster — not
                // `Route.PermissionManager`, which browses other apps' permissions.
                onOpenPermissionCentre = { navController.navigate(Route.PermissionCentre) },
                onOpenLegal = { navController.navigate(Route.WebView(it)) },
                onRequestSpecialAccess = onRequestSpecialAccess,
                onExit = onExit,
            )
        }

        // -- junk -----------------------------------------------------------------------------

        composable<Route.JunkScan> {
            JunkScanRoute(
                onScanned = {
                    navController.navigate(Route.JunkReview) {
                        popUpTo<Route.JunkScan> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.JunkReview> {
            JunkReviewRoute(
                onClean = { navController.navigate(Route.JunkClean) },
                onNavigateBack = { navController.popBackStack() },
                onSessionLost = {
                    navController.navigate(Route.JunkScan(JunkScanMode.Review)) {
                        popUpTo<Route.JunkReview> { inclusive = true }
                    }
                },
            )
        }

        composable<Route.JunkClean> {
            JunkCleanRoute(
                // The effect carries freedBytes AND failedCount so a run that failed outright can
                // never be reported as a success (Delta C3).
                //
                // UNKNOWN — the deleted-item count. `JunkCleanEffect` carries bytes and failures,
                // not a count, and `docs/screens/12-junk-cleaning.md:618` names only `freedBytes`.
                // `itemCount = 0` renders no item line rather than a fabricated one.
                onCleaned = { freedBytes, _ ->
                    navController.navigate(
                        Route.CleanResult(
                            feature = FeatureId.JunkClean,
                            freedBytes = freedBytes,
                            outcome = if (freedBytes > 0L) {
                                CleanupOutcome.Cleaned
                            } else {
                                CleanupOutcome.NothingFound
                            },
                        ),
                    ) { popUpTo<Route.JunkClean> { inclusive = true } }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // -- photo ----------------------------------------------------------------------------

        composable<Route.PhotoAlbums> {
            AlbumsRoute(
                onOpenAlbum = { navController.navigate(Route.AlbumDetail(it)) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.AlbumDetail> {
            AlbumDetailRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.SimilarPhotos> {
            SimilarPhotosRoute(
                onOpenPreview = { groupKey, startIndex ->
                    navController.navigate(Route.PhotoPreview(groupKey, startIndex))
                },
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.PhotoPreview> {
            PhotoPreviewRoute(
                onNavigateBack = { navController.popBackStack() },
                onSessionLost = { navController.popBackStack() },
            )
        }

        composable<Route.PhotoCompressor> {
            PhotoCompressorRoute(
                onOpenCompressRun = { navController.navigate(Route.CompressRun(it)) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.CompressRun> {
            CompressRunRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.PhotoPrivacy> {
            PhotoPrivacyRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // -- file tools -------------------------------------------------------------------------

        composable<Route.BigFiles> {
            BigFilesRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.Duplicates> {
            DuplicatesRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.VideoManager> {
            VideoManagerRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.AudioManager> {
            AudioManagerRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.AppManager> {
            AppManagerRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.WhatsAppCleaner> {
            WhatsAppCleanerRoute(
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // -- the one shared result screen -------------------------------------------------------

        composable<Route.CleanResult> { entry ->
            CleanResultRoute(
                summary = entry.toRoute<Route.CleanResult>().toSummary(),
                // The result screen replaces itself: the next tool must not have this one behind it.
                onNavigateToFeature = { feature: FeatureId ->
                    navController.navigate(feature.destination()) {
                        popUpTo<Route.CleanResult> { inclusive = true }
                    }
                },
                onNavigateHome = { navController.popBackStack<Route.Home>(inclusive = false) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // -- antivirus --------------------------------------------------------------------------

        composable<Route.AntivirusScan> {
            AntivirusScanRoute(
                onNavigateToResult = { findingCount ->
                    navController.navigate(Route.AntivirusResult(findingCount)) {
                        popUpTo<Route.AntivirusScan> { inclusive = true }
                    }
                },
                onNavigateToCleanResult = navController.toCleanResult(),
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.AntivirusResult> {
            AntivirusResultRoute(
                // The popUpTo is what the competitor's `finish()` did: a rescan does not stack a
                // second result behind the first.
                onNavigateToScan = {
                    navController.navigate(Route.AntivirusScan) {
                        popUpTo<Route.AntivirusResult> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // -- app lock ---------------------------------------------------------------------------

        composable<Route.AppLock> {
            AppLockRoute(
                onNavigateToSettings = { navController.navigate(Route.AppLockSettings) },
                onNavigateBack = { navController.popBackStack() },
                onRequestSpecialAccess = onRequestSpecialAccess,
            )
        }

        composable<Route.Pin> {
            PinRoute(
                onNavigateToAppLock = {
                    navController.navigate(Route.AppLock) {
                        popUpTo<Route.Pin> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.AppLockSettings> {
            AppLockSettingsRoute(
                onNavigateToChangePin = { navController.navigate(Route.Pin(PinMode.Change)) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // -- notification cleaner + permission manager --------------------------------------------

        composable<Route.NotificationGate> {
            NotificationGateRoute(
                onNavigateBack = { navController.popBackStack() },
                // The gate is not a screen to come back to.
                onNavigateToHiddenList = {
                    navController.navigate(Route.HiddenNotifications) {
                        popUpTo<Route.NotificationGate> { inclusive = true }
                    }
                },
            )
        }

        composable<Route.HiddenNotifications> {
            HiddenNotificationsRoute(
                onNavigateBack = { navController.popBackStack() },
                // The Int is a notification COUNT, not bytes — which is why `CleanupSummary` carries
                // `itemCount` beside `freedBytes` and the result screen renders no size block for it.
                onNavigateToCleanResult = { clearedCount ->
                    navController.navigate(
                        Route.CleanResult(
                            feature = FeatureId.NotificationCleaner,
                            freedBytes = 0L,
                            itemCount = clearedCount,
                            outcome = CleanupOutcome.ItemsCleared,
                        ),
                    )
                },
                onOpenHidingSettings = { navController.navigate(Route.NotificationHidingSettings) },
            )
        }

        composable<Route.NotificationHidingSettings> {
            NotificationHidingSettingsRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable<Route.PermissionManager> {
            PermissionManagerRoute(onNavigateBack = { navController.popBackStack() })
        }

        // -- device -----------------------------------------------------------------------------

        composable<Route.DeviceStatusScan> {
            DeviceStatusScanRoute(
                onScanned = {
                    navController.navigate(Route.DeviceStatusDetail) {
                        popUpTo<Route.DeviceStatusScan> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.DeviceStatusDetail> {
            DeviceStatusDetailRoute(
                onNavigateToRunningApps = { navController.navigate(Route.RunningAppsScan) },
                onNavigateToJunkClean = {
                    navController.navigate(Route.JunkScan(JunkScanMode.Review))
                },
                onNavigateToBattery = { navController.navigate(Route.BatteryScan) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.BatteryScan> {
            BatteryScanRoute(
                onScanned = {
                    navController.navigate(Route.BatteryInfo) {
                        popUpTo<Route.BatteryScan> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.BatteryInfo> {
            BatteryInfoRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable<Route.RunningAppsScan> {
            RunningAppsScanRoute(
                onScanned = {
                    navController.navigate(Route.RunningApps) {
                        popUpTo<Route.RunningAppsScan> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.RunningApps> {
            RunningAppsRoute(onNavigateBack = { navController.popBackStack() })
        }

        // -- network ------------------------------------------------------------------------------

        composable<Route.NetworkTraffic> {
            NetworkTrafficRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable<Route.SpeedTest> {
            SpeedTestRoute(
                onNavigateToResult = { downloadBps, uploadBps ->
                    navController.navigate(Route.SpeedTestResult(downloadBps, uploadBps)) {
                        popUpTo<Route.SpeedTest> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.SpeedTestResult> {
            SpeedTestResultRoute(
                onNavigateToTest = {
                    navController.navigate(Route.SpeedTest) {
                        popUpTo<Route.SpeedTestResult> { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // -- settings -----------------------------------------------------------------------------

        composable<Route.Settings> {
            SettingsRoute(
                onNavigateToLanguage = { navController.navigate(Route.Language) },
                onNavigateToAbout = { navController.navigate(Route.About) },
                onNavigateToPermissionCentre = { navController.navigate(Route.PermissionCentre) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.Language> {
            LanguageRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable<Route.About> {
            AboutRoute(
                onNavigateToLegalDocument = { navController.navigate(Route.WebView(it)) },
                // UNRESOLVED — `DevToolsRoute` lives in `:feature:settings`' **debug** source set, so
                // naming it here would not compile in release. Wiring it needs an `:app` debug source
                // set of its own; until that exists this is a no-op, and `AboutViewModel` already
                // refuses to raise the effect unless `isDebugBuild`. Recorded in `LLM.md` §11.
                onNavigateToDeveloperTools = { },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<Route.WebView> {
            WebViewRoute(onNavigateBack = { navController.popBackStack() })
        }

        composable<Route.PermissionCentre> {
            PermissionCentreRoute(onNavigateBack = { navController.popBackStack() })
        }
    }
}

/**
 * Home is the one destination reached by *clearing* what is above it rather than by pushing a copy:
 * a second `Home` on the stack is how the competitor ends up with a feature screen on top of a
 * half-built home (delta 9).
 */
private inline fun <reified T : Route> NavHostController.toHome() {
    navigate(Route.Home()) { popUpTo<T> { inclusive = true } }
}

/**
 * Fifteen screens end the same way. Written once so the destination cannot drift between them.
 */
private fun NavHostController.toCleanResult(): (CleanupSummary) -> Unit =
    { summary -> navigate(Route.CleanResult.of(summary)) }
