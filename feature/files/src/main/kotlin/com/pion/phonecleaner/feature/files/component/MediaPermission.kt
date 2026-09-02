package com.pion.phonecleaner.feature.files.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Which platform permission a media list needs, and what the current grant amounts to.
 *
 * **Platform truth lives in the composable layer, never in a ViewModel** (MVI §4): a ViewModel that
 * imported `android.os.Build` would be untestable on a bare JVM, which is why the Route resolves
 * this and reports it upward as `PermissionResolved(MediaAccess)`.
 *
 * The competitor has no equivalent at all — it checks a permission *outside* the screen and simply
 * does not start the Activity when the check fails, so partial access can only ever be an
 * all-or-nothing decision taken before the screen exists.
 */
internal enum class MediaKind {
    /** Video, and anything else in the visual collections. Has a partial-access form on API 34+. */
    Visual,

    /** Audio. `READ_MEDIA_AUDIO` has **no** user-selected variant — there is no partial arm. */
    Audio,
}

/**
 * The permissions to request, by API level.
 *
 * `READ_MEDIA_VISUAL_USER_SELECTED` is requested alongside `READ_MEDIA_VIDEO` on API 34+ because
 * that is what makes the photo-picker-style partial grant reachable at all; asking for the video
 * permission alone gives the user only "allow all" or "deny".
 *
 * All of these are declared in `:data`'s manifest, beside `DefaultMediaStoreRepository`, which is the
 * code that uses them — this module only *asks* (`feature/antivirus/src/main/AndroidManifest.xml`
 * states the same rule).
 */
internal fun mediaPermissions(kind: MediaKind): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && kind == MediaKind.Visual ->
        arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && kind == MediaKind.Visual ->
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)

    // API 32 and below: one permission covers every collection.
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/**
 * The current grant, read fresh. Called on every `ON_START`, so returning from the system dialog —
 * or from Settings, which the app never sees a result for — re-enters the same reducer.
 *
 * [MediaAccess.Partial] is a **normal outcome**, not a failure: on API 34+ the user may grant
 * `READ_MEDIA_VISUAL_USER_SELECTED` alone, and the list then shows exactly what they picked.
 */
internal fun mediaAccessOf(context: Context, kind: MediaKind): MediaAccess {
    val full = mediaPermissions(kind).first()
    if (context.isGranted(full)) return MediaAccess.Granted
    val partial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        kind == MediaKind.Visual &&
        context.isGranted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    return if (partial) MediaAccess.Partial else MediaAccess.Denied
}

private fun Context.isGranted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
