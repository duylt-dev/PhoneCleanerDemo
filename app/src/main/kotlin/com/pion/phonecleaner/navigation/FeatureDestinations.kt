package com.pion.phonecleaner.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.junk.JunkScanMode

/**
 * The one `when` over every feature id (`docs/screens/11-home.md` §3).
 *
 * It exists so `:feature:home` never names another cluster's route — that is what makes a
 * `:feature:A -> :feature:B` Gradle edge unnecessary rather than merely forbidden (`LLM.md` §2, §7.1).
 * Both callers that map a `FeatureId` to a destination — the home grid and the shared result
 * screen's "try another tool" row — come through here, so the two cannot drift apart. The
 * competitor's equivalent is `md.g1`, a router singleton switching on an untyped `goTag` whose two
 * `when` blocks **have** drifted (`LLM.md` §7.2).
 *
 * Being exhaustive over the enum is the point: adding a `FeatureId` without a destination is a
 * compile error here, not a tap that does nothing.
 */
fun FeatureId.destination(): Route = when (this) {
    // Every scan-then-review flow enters at its scan screen, which pops itself once it has a result.
    FeatureId.JunkClean -> Route.JunkScan(JunkScanMode.Review)
    FeatureId.Antivirus -> Route.AntivirusScan
    FeatureId.DeviceStatus -> Route.DeviceStatusScan
    FeatureId.BatteryInfo -> Route.BatteryScan
    FeatureId.RunningApps -> Route.RunningAppsScan

    // The notification cleaner enters at its permission gate, never at the list: the list without
    // the listener grant is an empty screen with no way to explain itself.
    FeatureId.NotificationCleaner -> Route.NotificationGate

    FeatureId.BigFiles -> Route.BigFiles
    FeatureId.DuplicateFiles -> Route.Duplicates
    FeatureId.VideoManager -> Route.VideoManager
    FeatureId.VideoCompressor -> Route.VideoCompressor
    FeatureId.AudioManager -> Route.AudioManager
    FeatureId.AppManager -> Route.AppManager
    FeatureId.WhatsAppCleaner -> Route.WhatsAppCleaner

    FeatureId.ImageManager -> Route.PhotoAlbums
    FeatureId.SimilarPhotos -> Route.SimilarPhotos
    FeatureId.BlurryPhotos -> Route.BlurryPhotos
    FeatureId.PhotoCompressor -> Route.PhotoCompressor
    FeatureId.PhotoPrivacy -> Route.PhotoPrivacy

    FeatureId.AppLock -> Route.AppLock

    // The per-app permission browser. NOT `Route.PermissionCentre`, which is this app's own roster.
    FeatureId.PermissionManager -> Route.PermissionManager()

    FeatureId.NetworkTraffic -> Route.NetworkTraffic
    FeatureId.NetworkTest -> Route.SpeedTest
}

/**
 * `LocalContext` in a Compose destination is a `ContextThemeWrapper` around the Activity, so a
 * single cast is not enough. Unwrapping is what lets `onExit` finish the host without `:app` holding
 * an Activity reference across recomposition.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
