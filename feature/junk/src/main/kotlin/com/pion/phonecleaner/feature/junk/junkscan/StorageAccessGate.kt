package com.pion.phonecleaner.feature.junk.junkscan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

/**
 * The storage gate the junk scan actually needs, kept out of `JunkScanRoute` so the Route stays a
 * composable and this stays testable platform logic.
 *
 * ## What this replaces, and why it was wrong
 *
 * Until 2026-09-06 the Route asked for `READ_MEDIA_IMAGES` / `_VIDEO` / `_AUDIO`. **The junk scan
 * issues no MediaStore query.** All three passes are `File.listFiles()` over the roots
 * `StorageRootProvider` reports, and not one of those three grants adds a root to that list. So the
 * screen asked for three permissions, was told yes, reported `PermissionsResolved(true)`, scanned the
 * app's own sandbox and rendered 0 B — the worst of the three possible outcomes, because "scanned,
 * found nothing" is indistinguishable from a clean device.
 *
 * The gate now asks the same question the scanner's roots answer: **can we read the shared volumes?**
 *
 * ## Three API branches, because the platform has three different answers
 *
 * | API | The grant | What a scan then covers |
 * |---|---|---|
 * | 30+ | `MANAGE_EXTERNAL_STORAGE`, given on a Settings page — no dialog exists | every mounted volume, minus `Android/data` and `Android/obb`, which stay unreadable (`gioi-han-android-phone-cleaner` §1) |
 * | 29 | `READ_EXTERNAL_STORAGE`, a runtime dialog | media plus this app's own directories. Scoped storage is enforced and `WRITE_EXTERNAL_STORAGE` is capped at API 28 in the manifest, so there is **no** all-files access to ask for. A real but much smaller scan |
 * | 28 | `READ_EXTERNAL_STORAGE`, a runtime dialog | the whole volume — API 28 predates scoped storage, which is why `minSdk` stayed at 28 |
 *
 * API 29 is the honest weak spot and it is not papered over: the gate opens, the scan runs, and it
 * covers less. `StorageRootProvider.coveredSurfaces()` is where that is reported, and it is the
 * reason a partial result may never be drawn as "everything scanned".
 */
internal fun hasFullStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

/**
 * True when the grant is a Settings page rather than a runtime dialog. The two need different
 * `ActivityResultContract`s, and picking the wrong one is silent: `RequestMultiplePermissions` on
 * `MANAGE_EXTERNAL_STORAGE` returns denied immediately without ever showing the user anything.
 */
internal fun needsAllFilesSettingsPage(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

/**
 * The all-files Settings page, scoped to this app so the user lands on our row instead of a list of
 * every installed app.
 *
 * `FLAG_ACTIVITY_NEW_TASK` is deliberately not set — it is launched through an
 * `ActivityResultLauncher` from the hosting Activity, so the page sits on our task and Back lands
 * on the scan screen, where `LifecycleStartEffect` re-reads the gate. This mirrors
 * `:data/permission/SpecialAccessIntents.kt`, which `:feature:*` may not import (`LLM.md` §2).
 */
internal fun allFilesSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
    Uri.fromParts("package", context.packageName, null),
)

/**
 * The runtime permissions for API 28 and 29.
 *
 * `WRITE_EXTERNAL_STORAGE` is included only on API 28, matching its `maxSdkVersion` in
 * `data/src/main/AndroidManifest.xml`: on 28 it is what makes a delete on shared storage possible,
 * and on 29 requesting a permission the manifest caps below the running API returns a permanent
 * denial that the user is never shown.
 */
internal fun legacyStoragePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
