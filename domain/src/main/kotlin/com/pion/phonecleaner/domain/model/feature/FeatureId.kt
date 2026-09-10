package com.pion.phonecleaner.domain.model.feature

import kotlinx.serialization.Serializable

/**
 * The one feature enum. Twenty-three constants: twenty verbatim from the competitor's own tables, and
 * [BlurryPhotos], [VideoCompressor] and [Trash], which are this app's own and are marked as such below.
 *
 * The competitor kept these three facts in three unrelated places — a descriptor array index, a
 * `goTag` int on the router, and a deep-link `action_id` — and they had to be edited together by hand.
 * Keeping them on one enum makes that impossible to get wrong.
 *
 * `@StringRes` / `@DrawableRes` deliberately do NOT live here: this module has no Android types.
 * They belong to `FeatureDescriptor` in `:core:ui/catalog/` (LLM.md §3.5).
 *
 * Source: `docs/system-architecture.md:334`, derived in `docs/reverse-engineering/21-shared-models-and-ui.md`.
 * `analyticsType` — a fourth property proposed by r2-09 — is deliberately absent: it is still UNKNOWN
 * (r3-04 §3B), and a fabricated value would look checked.
 */
@Serializable
enum class FeatureId(
    /** descriptor index == goTag == deep-link `action_id`. Three names, one number. */
    val legacyIndex: Int,
    /** analytics id. Kept on the enum so a destination and its event can never be edited apart. */
    val analyticsId: String,
    /** The SharedPreferences key the competitor wrote. Read ONCE, by the migration worker. */
    val legacyPrefKey: String,
) {
    JunkClean          ( 1, "100505", "flux_gn_use_flux_real_clean"),
    BigFiles           ( 2, "100506", "flux_gn_use_flux_big_file"),
    AppManager         ( 3, "100509", "flux_gn_use_flux_app"),
    Antivirus          ( 4, "100507", "flux_gn_use_flux_anti"),
    ImageManager       ( 5, "100510", "flux_gn_use_flux_image"),
    VideoManager       ( 6, "100511", "flux_gn_use_flux_video"),
    AudioManager       ( 7, "100512", "flux_gn_use_flux_audio"),
    NotificationCleaner( 9, "100513", "flux_gn_use_flux_not_cleaner"),
    SimilarPhotos      (10, "100514", "flux_gn_use_flux_pho_sim"),
    PhotoCompressor    (11, "100515", "flux_gn_use_flux_pho_compress"),
    PermissionManager  (12, "100518", "flux_gn_use_flux_perm"),
    PhotoPrivacy       (13, "100516", "flux_gn_use_flux_pho_pri"),
    DuplicateFiles     (15, "100517", "flux_gn_use_flux_dup_file"),
    AppLock            (16, "100520", "flux_gn_use_flux_app_lock"),
    WhatsAppCleaner    (17, "100523", "flux_gn_use_flux_whats_app"),
    BatteryInfo        (18, "100525", "flux_gn_use_flux_battery"),
    RunningApps        (19, "100526", "flux_gn_use_flux_running_apps"),
    DeviceStatus       (20, "100527", "flux_gn_use_flux_device_status"),
    NetworkTraffic     (21, "100528", "flux_gn_use_flux_network_traffic"),
    NetworkTest        (22, "100529", "flux_gn_use_flux_network_test"),

    /**
     * **The first constant here that does NOT come from the competitor.** It has no descriptor index
     * to inherit, no analytics id to match and no `SharedPreferences` key to migrate, because
     * `com.againstvirus.flux` has no such screen: `docs/reverse-engineering/13-photo-and-media.md:544`
     * records that no sharpness, resolution or size heuristic exists anywhere in that APK.
     *
     * So the three columns are **ours**, and each is chosen to be unmistakably ours rather than to
     * look inherited:
     *
     *  - `legacyIndex = 23` continues past the competitor's highest (22). It is not a legacy index at
     *    all; it is a `goTag` and a deep-link `action_id` that only this app will ever emit. Keeping
     *    it in the same sequence is what lets `fromLegacyIndex` stay one lookup over one list.
     *  - `analyticsId = "100530"` likewise continues the sequence. No competitor event carries it.
     *  - `legacyPrefKey = ""` — **empty on purpose.** The migration worker reads this column once, to
     *    find what the competitor's install had recorded; there is nothing to find for a feature that
     *    never existed there. An invented `flux_…` key would be a key the worker would go looking for
     *    and never match, which is a lie that costs a lookup on every migration.
     */
    BlurryPhotos       (23, "100530", ""),

    /**
     * **The second constant here that does NOT come from the competitor**, after [BlurryPhotos].
     * `grep -ri 'transcod\|MediaCodec\|MediaMuxer\|video.*compress' docs/` finds nothing in
     * `com.againstvirus.flux`: it has a photo compressor and no video equivalent, so there is no
     * descriptor index, no analytics id and no `SharedPreferences` key to inherit.
     *
     * The three columns are **ours**, chosen the same way [BlurryPhotos]'s were:
     *
     *  - `legacyIndex = 24` continues past 23. It is not a legacy index; it is a `goTag` and a
     *    deep-link `action_id` only this app will ever emit, kept in sequence so `fromLegacyIndex`
     *    stays one lookup over one list.
     *  - `analyticsId = "100531"` likewise continues the sequence. No competitor event carries it.
     *  - `legacyPrefKey = ""` — **empty on purpose.** The migration worker reads this column once to
     *    find what a competitor install had recorded, and there is nothing to find for a feature
     *    that never existed there. An invented `flux_…` key would be a lookup that can only miss.
     */
    VideoCompressor    (24, "100531", ""),

    /**
     * **The third constant here that does NOT come from the competitor**, after [BlurryPhotos] and
     * [VideoCompressor]. `com.againstvirus.flux` has no recycle bin at all: its whole delete strategy
     * is `deleteRecursively` inside `catch (Exception) { printStackTrace() }`
     * (`MenaremovActivity.java:242`), which has nowhere to put a file it is about to destroy.
     *
     * The three columns are **ours**, chosen the same way the two above were:
     *
     *  - `legacyIndex = 25` continues past 24. It is not a legacy index; it is a `goTag` and a
     *    deep-link `action_id` only this app will ever emit, kept in sequence so `fromLegacyIndex`
     *    stays one lookup over one list.
     *  - `analyticsId = "100532"` likewise continues the sequence. No competitor event carries it.
     *  - `legacyPrefKey = ""` — **empty on purpose.** The migration worker reads this column once to
     *    find what a competitor install had recorded, and there is nothing to find for a feature that
     *    never existed there. An invented `flux_…` key would be a lookup that can only miss.
     */
    Trash              (25, "100532", "");

    companion object {
        fun fromLegacyIndex(i: Int): FeatureId? = entries.firstOrNull { it.legacyIndex == i }
    }
}
