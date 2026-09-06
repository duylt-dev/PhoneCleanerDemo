package com.pion.phonecleaner.navigation

import com.pion.phonecleaner.domain.model.applock.PinMode
import com.pion.phonecleaner.domain.model.cleanup.CleanupOutcome
import com.pion.phonecleaner.domain.model.cleanup.CleanupSummary
import com.pion.phonecleaner.domain.model.feature.FeatureId
import com.pion.phonecleaner.domain.model.junk.JunkScanMode
import com.pion.phonecleaner.domain.model.launch.LaunchSource
import com.pion.phonecleaner.domain.model.photo.PhotoSessionSource
import com.pion.phonecleaner.domain.model.settings.LegalDocument
import com.pion.phonecleaner.feature.notification.permissionmanager.PermissionTab
import kotlinx.serialization.Serializable

/**
 * Every destination, as a type. Type-safe navigation (LLM.md §7.2): an argument that does not
 * compile cannot be passed, which is the failure the competitor's int-tagged router singleton
 * (`md.g1`, 55 branches over an untyped `goTag`) makes routine.
 *
 * One route per screen is added here in the same change that adds the screen.
 *
 * ### The property name IS the `SavedStateHandle` key
 *
 * Under type-safe navigation a route argument is stored under its **property name**, so every
 * property below is named to match a constant a feature module already declares and reads. Those
 * constants are the contract; renaming a property here silently breaks the read at the far end,
 * because `SavedStateHandle.get<T>` is an unchecked cast that fails at the use site rather than at
 * the boundary. Each one is named in a comment beside its property.
 *
 * ### `LockScreenActivity` is deliberately absent
 *
 * It must appear over *another* app, so it is an Activity and not a NavHost destination
 * (LLM.md §7.5). Its argument travels as an Intent extra keyed by `LockScreenArgs.PACKAGE_NAME`.
 */
sealed interface Route {

    // -- onboarding ---------------------------------------------------------------------------

    /** `source` matches `SplashArgs`' private `ARG_SOURCE`. */
    @Serializable
    data class Splash(val source: LaunchSource) : Route

    @Serializable
    data object DeviceCheck : Route

    /** A `dialog { }` destination on the existing back stack, not a full screen (LLM.md §7.5). */
    @Serializable
    data object AppResume : Route

    // -- home ---------------------------------------------------------------------------------

    /**
     * `fromNotification` and `feature` match `HomeArgs.FROM_NOTIFICATION` / `HomeArgs.FEATURE`.
     *
     * `feature` is a `String?` and not a `FeatureId?`: Navigation encodes a non-null enum as its
     * `name`, but has no built-in `NavType` for a nullable one. `HomeArgs.pendingFeature()` already
     * reads either shape, so the String form costs nothing and adds no custom `NavType` to keep in
     * step with the enum.
     */
    @Serializable
    data class Home(
        val fromNotification: Boolean = false,
        val feature: String? = null,
    ) : Route

    // -- junk ---------------------------------------------------------------------------------

    /** `mode` matches `JunkScanViewModel`'s private `ARG_MODE`. */
    @Serializable
    data class JunkScan(val mode: JunkScanMode) : Route

    @Serializable
    data object JunkReview : Route

    @Serializable
    data object JunkClean : Route

    // -- photo --------------------------------------------------------------------------------

    @Serializable
    data object PhotoAlbums : Route

    /** `folderName` matches `AlbumDetailViewModel.FOLDER_NAME_ARG`. */
    @Serializable
    data class AlbumDetail(val folderName: String) : Route

    @Serializable
    data object SimilarPhotos : Route

    /** The blurry-photo grid. No argument: the scan is started by the screen and kept in its store. */
    @Serializable
    data object BlurryPhotos : Route

    /**
     * `groupKey` / `startIndex` / `source` match `PhotoPreviewViewModel.GROUP_KEY_ARG` /
     * `START_INDEX_ARG` / `SOURCE_ARG`.
     *
     * `source` has **no default**: the pager reads whichever session store it names, and the two
     * grids keep separate ones. A default would let a new caller open the pager over the wrong
     * session and see a "session lost" screen instead of a compile error.
     */
    @Serializable
    data class PhotoPreview(
        val groupKey: String,
        val startIndex: Int,
        val source: PhotoSessionSource,
    ) : Route

    @Serializable
    data object PhotoCompressor : Route

    /** `photoIds` matches `CompressRunViewModel.PHOTO_IDS_ARG`; `List<Long>` is a built-in NavType. */
    @Serializable
    data class CompressRun(val photoIds: List<Long>) : Route

    @Serializable
    data object PhotoPrivacy : Route

    // -- files --------------------------------------------------------------------------------

    @Serializable
    data object BigFiles : Route

    @Serializable
    data object Duplicates : Route

    @Serializable
    data object VideoManager : Route

    @Serializable
    data object AudioManager : Route

    @Serializable
    data object AppManager : Route

    @Serializable
    data object WhatsAppCleaner : Route

    // -- the shared result screen -------------------------------------------------------------

    /**
     * `CleanupSummary` is spread over four primitive properties rather than passed as one
     * `@Serializable` object, because a class argument needs a hand-written `NavType` that has to be
     * kept in step with the class by hand. [toSummary] rebuilds it in the one place that needs it.
     */
    @Serializable
    data class CleanResult(
        val feature: FeatureId,
        val freedBytes: Long = 0L,
        val itemCount: Int = 0,
        val outcome: CleanupOutcome,
    ) : Route {
        fun toSummary(): CleanupSummary = CleanupSummary(
            feature = feature,
            freedBytes = freedBytes,
            itemCount = itemCount,
            outcome = outcome,
        )

        companion object {
            fun of(summary: CleanupSummary): CleanResult = CleanResult(
                feature = summary.feature,
                freedBytes = summary.freedBytes,
                itemCount = summary.itemCount,
                outcome = summary.outcome,
            )
        }
    }

    // -- antivirus ----------------------------------------------------------------------------

    @Serializable
    data object AntivirusScan : Route

    /** `findingCount` matches `AntivirusResultArgs`' private `ARG_FINDING_COUNT`. */
    @Serializable
    data class AntivirusResult(val findingCount: Int) : Route

    // -- app lock -----------------------------------------------------------------------------

    @Serializable
    data object AppLock : Route

    /** `mode` matches `PinArgs.MODE`. */
    @Serializable
    data class Pin(val mode: PinMode) : Route

    @Serializable
    data object AppLockSettings : Route

    // -- notification cleaner + permission manager --------------------------------------------

    @Serializable
    data object NotificationGate : Route

    @Serializable
    data object HiddenNotifications : Route

    @Serializable
    data object NotificationHidingSettings : Route

    /** `tab` matches `TAB_KEY` in `PermissionManagerArguments.kt`. */
    @Serializable
    data class PermissionManager(val tab: PermissionTab = PermissionTab.Apps) : Route

    // -- device -------------------------------------------------------------------------------

    @Serializable
    data object DeviceStatusScan : Route

    @Serializable
    data object DeviceStatusDetail : Route

    @Serializable
    data object BatteryScan : Route

    @Serializable
    data object BatteryInfo : Route

    @Serializable
    data object RunningAppsScan : Route

    @Serializable
    data object RunningApps : Route

    // -- network ------------------------------------------------------------------------------

    @Serializable
    data object NetworkTraffic : Route

    @Serializable
    data object SpeedTest : Route

    /**
     * `downloadBps` / `uploadBps` match `SpeedTestResultViewModel.ARG_DOWNLOAD_BPS` / `ARG_UPLOAD_BPS`.
     * Both defaults are `0L` and must stay so: a default declared here and not in the reader clears
     * the back stack on a configuration change.
     */
    @Serializable
    data class SpeedTestResult(
        val downloadBps: Long = 0L,
        val uploadBps: Long = 0L,
    ) : Route

    // -- settings -----------------------------------------------------------------------------

    @Serializable
    data object Settings : Route

    @Serializable
    data object Language : Route

    @Serializable
    data object About : Route

    /** `document` matches `WebViewArgs.DOCUMENT`. */
    @Serializable
    data class WebView(val document: LegalDocument) : Route

    /** The settings-owned permission roster. NOT [PermissionManager], which browses other apps. */
    @Serializable
    data object PermissionCentre : Route
}
