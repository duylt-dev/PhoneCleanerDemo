package com.pion.phonecleaner.data.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat

/**
 * The four file-access predicates of [AndroidPermissionRepository], split out because they are the
 * one part of the permission layer that **moves with the storage branch**
 * (`docs/system-architecture.md` §8.4): when the branch changes, this file and `storageDataModule`
 * change and nothing else does. The other seven predicates — overlay, usage access, notification
 * listener and the rest — are fixed platform switches and never move.
 *
 * It is not a port and it is not in Koin: no state, one constructor argument, and exactly one caller.
 */
internal class StorageAccessChecks(private val context: Context) {

    /**
     * `READ_EXTERNAL_STORAGE` below API 33, where it is the only form there is.
     *
     * UNKNOWN — what `AppPermission.Storage` means on API 33+. `docs/system-architecture.md` §8.3
     * splits the read permission into the three `READ_MEDIA_*` grants there and says nothing about
     * the older constant; looked in §8.1–§8.4, in `docs/reverse-engineering/02` §2.1 (which documents
     * only the competitor's `SDK_INT < 30` write branch) and in `AppPermission`'s own definition.
     * Answering `false` on every modern device would report a screen as blocked that the media grant
     * already unblocks, so `Storage` defers to [media] there — one meaning, not a second one.
     */
    fun legacyStorage(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            media()
        } else {
            hasRuntime(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    /**
     * **Never assumed grantable** (`docs/system-architecture.md` §8.1, an owner decision). The
     * predicate exists so an all-files build can express the state; the default branch is
     * `MediaStore` + SAF, and no default-branch feature may declare `AllFiles` as a precondition.
     * `data/src/main/AndroidManifest.xml` therefore does not declare `MANAGE_EXTERNAL_STORAGE`, so on
     * API 30+ this reads `false` until a build that does declare it is made — the intended answer,
     * not a defect.
     *
     * Below API 30 the equivalent is the legacy read **and** write pair, which is what `od.z.x`
     * checks. On API 29 that reads `false`, because `WRITE_EXTERNAL_STORAGE` is capped at API 28 in
     * the manifest and on 29 scoped storage means there is no all-files access to have.
     */
    fun allFiles(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            hasRuntime(Manifest.permission.READ_EXTERNAL_STORAGE) &&
                hasRuntime(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    /**
     * Strategy 3 of `docs/system-architecture.md` §8.3, and the default branch's main surface.
     *
     * **Android 14 partial access is a normal outcome, not a failure** (§8.3): with
     * `READ_MEDIA_VISUAL_USER_SELECTED` the user picked specific items, `MediaStore` returns those,
     * and a scan over them is a real scan of a smaller set. The competitor reaches the same
     * conclusion in `od.z.h0()`, accepting the partial grant as sufficient for its image and video
     * checks.
     */
    fun media(): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            hasRuntime(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> true

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            hasRuntime(Manifest.permission.READ_MEDIA_IMAGES) &&
                hasRuntime(Manifest.permission.READ_MEDIA_VIDEO) &&
                hasRuntime(Manifest.permission.READ_MEDIA_AUDIO)

        else -> hasRuntime(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    /**
     * A persisted, still-valid SAF read grant over a tree whose URI names WhatsApp.
     *
     * The grant is read from `ContentResolver.persistedUriPermissions` — the live list — and not from
     * the DataStore copy `StorageAccessPrefs.PERSISTED_TREE_URIS` holds, because a
     * `takePersistableUriPermission` grant survives a reboot but not an uninstall and not a user
     * revocation (§8.4). A permission predicate that answered from the stored copy would report a
     * revoked tree as held.
     *
     * UNKNOWN — how this grant is identified. `WhatsAppRoots` belongs to `filesDataModule`
     * (`docs/system-architecture.md` §5.7) and is not written yet, so there is no root list to match
     * against; looked in §8.3/§8.4, `docs/screens/14` §0.2 and §6.2, and `AppPermission`'s own
     * definition, none of which states the test. The match below is deliberately conservative: a
     * false negative asks the user for the tree again, which costs a tap, while a false positive
     * would claim a scan covered a folder it cannot read.
     */
    fun whatsAppTree(): Boolean =
        context.contentResolver.persistedUriPermissions.any { grant ->
            grant.isReadPermission && grant.uri.toString().contains(WHATSAPP_TREE_TOKEN, ignoreCase = true)
        }

    private fun hasRuntime(name: String): Boolean =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val WHATSAPP_TREE_TOKEN = "WhatsApp"
    }
}
