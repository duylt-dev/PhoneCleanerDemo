package com.pion.phonecleaner.core.ui.catalog

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Videocam
import com.pion.phonecleaner.core.ui.R
import com.pion.phonecleaner.domain.model.feature.FeatureId

/**
 * One descriptor per `FeatureId`, in declaration order. This is `ae.i2` (621 lines), `ae.o0`
 * (538 lines) and `qd.a` (119 lines) reduced to a lookup (`docs/screens/21` §5).
 *
 * A pure `object` and **not in Koin**, for the same reason `FeatureCatalog` is not: a dependency-free
 * lookup that holds no state and touches no platform gains nothing from injection, and two proposals
 * to declare it were rejected (`docs/screens/21` §5.2, §6.1).
 *
 * Every icon is a `androidx.compose.material.icons` vector, **not** a competitor asset — see
 * `FeatureDescriptor`'s KDoc, deviation 1. They are chosen to be legible, not to match the original
 * art, which this repository does not have.
 *
 * The `exitCta` column is the four-way `if` §5.2 describes, made into data: clean, review, check,
 * open. Titles and descriptions live in `core/ui/src/main/res/values/strings.xml` and are subject to
 * the wording ban (LLM.md §1/§5) — which is why `FeatureId.Antivirus` reads "App safety check", the
 * owner's replacement framing (`STATE.md:48`), and why `RunningApps` says what it lists rather than
 * what removing something from it would supposedly do.
 */
object FeatureDescriptors {

    fun of(feature: FeatureId): FeatureDescriptor = byId.getValue(feature)

    /** In `FeatureId` declaration order. A section of home renders a slice of this. */
    val all: List<FeatureDescriptor> = listOf(
        FeatureDescriptor(
            FeatureId.JunkClean, R.string.feature_junk_clean_title,
            R.string.feature_junk_clean_description, Icons.Filled.CleaningServices,
            R.string.feature_cta_clean,
        ),
        FeatureDescriptor(
            FeatureId.BigFiles, R.string.feature_big_files_title,
            R.string.feature_big_files_description, Icons.Filled.FolderOpen,
            R.string.feature_cta_review,
        ),
        FeatureDescriptor(
            FeatureId.AppManager, R.string.feature_app_manager_title,
            R.string.feature_app_manager_description, Icons.Filled.Apps,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.Antivirus, R.string.feature_antivirus_title,
            R.string.feature_antivirus_description, Icons.Filled.Security,
            R.string.feature_cta_check,
        ),
        FeatureDescriptor(
            FeatureId.ImageManager, R.string.feature_image_manager_title,
            R.string.feature_image_manager_description, Icons.Filled.Image,
            R.string.feature_cta_review,
        ),
        FeatureDescriptor(
            FeatureId.VideoManager, R.string.feature_video_manager_title,
            R.string.feature_video_manager_description, Icons.Filled.Videocam,
            R.string.feature_cta_review,
        ),
        FeatureDescriptor(
            FeatureId.AudioManager, R.string.feature_audio_manager_title,
            R.string.feature_audio_manager_description, Icons.Filled.MusicNote,
            R.string.feature_cta_review,
        ),
        FeatureDescriptor(
            FeatureId.NotificationCleaner, R.string.feature_notification_cleaner_title,
            R.string.feature_notification_cleaner_description, Icons.Filled.Notifications,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.SimilarPhotos, R.string.feature_similar_photos_title,
            R.string.feature_similar_photos_description, Icons.Filled.PhotoLibrary,
            R.string.feature_cta_review,
        ),
        FeatureDescriptor(
            FeatureId.PhotoCompressor, R.string.feature_photo_compressor_title,
            R.string.feature_photo_compressor_description, Icons.Filled.Compress,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.PermissionManager, R.string.feature_permission_manager_title,
            R.string.feature_permission_manager_description, Icons.Filled.VerifiedUser,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.PhotoPrivacy, R.string.feature_photo_privacy_title,
            R.string.feature_photo_privacy_description, Icons.Filled.PhotoAlbum,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.DuplicateFiles, R.string.feature_duplicate_files_title,
            R.string.feature_duplicate_files_description, Icons.Filled.ContentCopy,
            R.string.feature_cta_review,
        ),
        FeatureDescriptor(
            FeatureId.AppLock, R.string.feature_app_lock_title,
            R.string.feature_app_lock_description, Icons.Filled.Lock,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.WhatsAppCleaner, R.string.feature_whatsapp_cleaner_title,
            R.string.feature_whatsapp_cleaner_description, Icons.AutoMirrored.Filled.Chat,
            R.string.feature_cta_review,
        ),
        FeatureDescriptor(
            FeatureId.BatteryInfo, R.string.feature_battery_info_title,
            R.string.feature_battery_info_description, Icons.Filled.BatteryFull,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.RunningApps, R.string.feature_running_apps_title,
            R.string.feature_running_apps_description, Icons.Filled.Memory,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.DeviceStatus, R.string.feature_device_status_title,
            R.string.feature_device_status_description, Icons.Filled.PhoneAndroid,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.NetworkTraffic, R.string.feature_network_traffic_title,
            R.string.feature_network_traffic_description, Icons.Filled.DataUsage,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.NetworkTest, R.string.feature_network_test_title,
            R.string.feature_network_test_description, Icons.Filled.NetworkCheck,
            R.string.feature_cta_open,
        ),
        FeatureDescriptor(
            FeatureId.BlurryPhotos, R.string.feature_blurry_photos_title,
            R.string.feature_blurry_photos_description, Icons.Filled.BlurOn,
            R.string.feature_cta_review,
        ),
        FeatureDescriptor(
            FeatureId.VideoCompressor, R.string.feature_video_compressor_title,
            R.string.feature_video_compressor_description, Icons.Filled.Movie,
            R.string.feature_cta_review,
        ),
    )

    private val byId: Map<FeatureId, FeatureDescriptor> = all.associateBy(FeatureDescriptor::feature)

    init {
        // One descriptor per feature. `of()` uses getValue, so a missing row would throw at
        // the first render of the tile that needs it — on a device, not here. This turns that into a
        // failure at class-load, which every unit test touching the catalogue hits immediately.
        check(byId.size == FeatureId.entries.size) {
            "FeatureDescriptors is missing: ${FeatureId.entries - byId.keys}"
        }
    }
}
